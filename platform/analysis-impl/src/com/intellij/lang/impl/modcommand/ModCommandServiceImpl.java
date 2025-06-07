// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.options.OptControl;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.modcommand.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class ModCommandServiceImpl implements ModCommandService {
  @Override
  public @NotNull IntentionAction wrap(@NotNull ModCommandAction action) {
    return new ModCommandActionWrapper(action, null);
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement wrapToLocalQuickFixAndIntentionActionOnPsiElement(@NotNull ModCommandAction action,
                                                                                                                @NotNull PsiElement psiElement) {
    return new ModCommandActionQuickFixUberWrapper(action, psiElement);
  }

  @Override
  public @NotNull LocalQuickFix wrapToQuickFix(@NotNull ModCommandAction action) {
    return new ModCommandActionQuickFixWrapper(action);
  }

  @Override
  public @Nullable ModCommandAction unwrap(@NotNull LocalQuickFix fix) {
    if (fix instanceof ModCommandActionQuickFixWrapper wrapper) {
      return wrapper.getAction();
    }
    return null;
  }

  @Override
  public @NotNull ModCommand psiUpdate(@NotNull ActionContext context, @NotNull Consumer<@NotNull ModPsiUpdater> updater) {
    return PsiUpdateImpl.psiUpdate(context, updater);
  }

  @Override
  public <T extends InspectionProfileEntry> @NotNull ModCommand updateOption(
    @NotNull PsiElement context, @NotNull T inspection, @NotNull Consumer<@NotNull T> updater) {

    InspectionProfileEntry copiedTool = getToolCopy(context, inspection);
    List<@NotNull OptControl> controls = inspection.getOptionsPane().allControls();
    final Element options = new Element("copy");
    inspection.writeSettings(options);
    copiedTool.readSettings(options);
    //noinspection unchecked
    updater.accept((T)copiedTool);
    OptionController oldController = inspection.getOptionController();
    OptionController newController = copiedTool.getOptionController();
    List<ModUpdateSystemOptions.ModifiedOption> modifiedOptions = new ArrayList<>();
    for (OptControl control : controls) {
      Object oldValue = oldController.getOption(control.bindId());
      Object newValue = newController.getOption(control.bindId());
      if (oldValue != null && newValue != null && !oldValue.equals(newValue)) {
        String bindId = "currentProfile." + inspection.getShortName() + ".options." + control.bindId();
        modifiedOptions.add(new ModUpdateSystemOptions.ModifiedOption(bindId, oldValue, newValue));
      }
    }
    return modifiedOptions.isEmpty() ? ModCommand.nop() : new ModUpdateSystemOptions(modifiedOptions);
  }

  private static @NotNull <T extends InspectionProfileEntry> InspectionProfileEntry getToolCopy(@NotNull PsiElement context, @NotNull T inspection) {
    InspectionToolWrapper<?, ?> tool = InspectionProfileManager.getInstance(context.getProject())
      .getCurrentProfile().getInspectionTool(inspection.getShortName(), context);
    if (tool == null) {
      throw new IllegalArgumentException("Tool not found: " + inspection.getShortName());
    }
    InspectionProfileEntry copiedTool = tool.createCopy().getTool();
    if (copiedTool.getClass() != inspection.getClass()) {
      if (copiedTool instanceof GlobalInspectionTool global) {
        LocalInspectionTool local = global.getSharedLocalInspectionTool();
        if (local != null) {
          copiedTool = local;
        }
      }
      if (copiedTool.getClass() != inspection.getClass()) {
        throw new IllegalArgumentException(
          "Invalid class: " + copiedTool.getClass() + "!=" + inspection.getClass() + " (id: " + inspection.getShortName() + ")");
      }
    }
    return copiedTool;
  }

  @Override
  public @Nullable ModCommandWithContext chooseFileAndPerform(@NotNull PsiFile hostFile,
                                                              @Nullable Editor hostEditor,
                                                              @NotNull ModCommandAction commandAction,
                                                              int fixOffset) {
    Project project = hostFile.getProject();
    ThrowableComputable<ModCommandWithContext, RuntimeException> computable =
      () -> ReadAction.nonBlocking(() -> {
          ActionContext context = chooseContextForAction(hostFile, hostEditor, commandAction, fixOffset);
          if (context == null) {
            return new ModCommandWithContext(ActionContext.from(null, hostFile), ModCommand.nop());
          }
          return new ModCommandWithContext(context, commandAction.perform(context));
        })
        .expireWhen(() -> project.isDisposed())
        .executeSynchronously();
    //noinspection DialogTitleCapitalization
    return ProgressManager.getInstance().
      runProcessWithProgressSynchronously(computable, commandAction.getFamilyName(), true, project);
  }

  @RequiresBackgroundThread
  private static @Nullable ActionContext chooseContextForAction(@NotNull PsiFile hostFile,
                                                                @Nullable Editor hostEditor,
                                                                @NotNull ModCommandAction commandAction,
                                                                int fixOffset) {
    if (hostEditor == null) {
      return ActionContext.from(null, hostFile);
    }
    int offset = fixOffset >= 0 ? fixOffset : hostEditor.getCaretModel().getOffset();
    ActionContext hostContext = ActionContext.from(hostEditor, hostFile).withOffset(offset);
    PsiFile injectedFile = InjectedLanguageUtilBase.findInjectedPsiNoCommit(hostFile, offset);
    if (injectedFile != null) {
      ActionContext injectedContext = hostContext.mapToInjected(injectedFile);
      if (commandAction.getPresentation(injectedContext) != null) {
        return injectedContext;
      }
    }

    if (commandAction.getPresentation(hostContext) != null) {
      return hostContext.withOffset(offset);
    }
    return null;
  }
}
