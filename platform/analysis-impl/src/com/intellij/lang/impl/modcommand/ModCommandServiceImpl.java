// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.options.OptControl;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.modcommand.*;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ModCommandServiceImpl implements ModCommandService {
  @Override
  public @NotNull IntentionAction wrap(@NotNull ModCommandAction action) {
    return new ModCommandActionWrapper(action);
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

  @NotNull
  private static <T extends InspectionProfileEntry> InspectionProfileEntry getToolCopy(@NotNull PsiElement context, @NotNull T inspection) {
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
}
