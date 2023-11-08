// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.options.*;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ObjectUtils;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.openapi.util.text.HtmlChunk.tag;
import static com.intellij.openapi.util.text.HtmlChunk.text;

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
    List<ModUpdateInspectionOptions.ModifiedInspectionOption> modifiedOptions = new ArrayList<>();
    for (OptControl control : controls) {
      Object oldValue = oldController.getOption(control.bindId());
      Object newValue = newController.getOption(control.bindId());
      if (oldValue != null && newValue != null && !oldValue.equals(newValue)) {
        modifiedOptions.add(new ModUpdateInspectionOptions.ModifiedInspectionOption(control.bindId(), oldValue, newValue));
      }
    }
    return modifiedOptions.isEmpty() ? ModCommand.nop() : new ModUpdateInspectionOptions(inspection.getShortName(), modifiedOptions);
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

  @ApiStatus.Experimental
  @Override
  public @NotNull IntentionPreviewInfo getPreview(@NotNull ModCommand modCommand, @NotNull ActionContext context) {
    Project project = context.project();
    PsiFile file = context.file();
    List<IntentionPreviewInfo.CustomDiff> customDiffList = new ArrayList<>();
    IntentionPreviewInfo navigateInfo = IntentionPreviewInfo.EMPTY;
    for (ModCommand command : modCommand.unpack()) {
      if (command instanceof ModUpdateFileText modFile) {
        VirtualFile vFile = modFile.file();
        var currentFile =
          vFile.equals(file.getOriginalFile().getVirtualFile()) ||
          vFile.equals(InjectedLanguageManager.getInstance(project).getTopLevelFile(file).getOriginalFile().getVirtualFile());
        customDiffList.add(new IntentionPreviewInfo.CustomDiff(vFile.getFileType(),
                                                               currentFile ? null : vFile.getName(), modFile.oldText(), modFile.newText(), true));
      }
      else if (command instanceof ModCreateFile createFile) {
        VirtualFile vFile = createFile.file();
        customDiffList.add(new IntentionPreviewInfo.CustomDiff(vFile.getFileType(), vFile.getName(), "", createFile.text(), true));
      }
      else if (command instanceof ModNavigate navigate && navigate.caret() != -1) {
        PsiFile target = PsiManager.getInstance(project).findFile(navigate.file());
        if (target != null) {
          navigateInfo = IntentionPreviewInfo.navigate(target, navigate.caret());
        }
      }
      else if (command instanceof ModChooseAction target) {
        return getChoosePreview(context, target);
      }
      else if (command instanceof ModChooseMember target) {
        return getPreview(target.nextCommand().apply(target.defaultSelection()), context);
      }
      else if (command instanceof ModShowConflicts showConflicts) {
        return getPreview(showConflicts.nextStep(), context);
      }
      else if (command instanceof ModDisplayMessage message) {
        if (message.kind() == ModDisplayMessage.MessageKind.ERROR) {
          return new IntentionPreviewInfo.Html(new HtmlBuilder().append(
            AnalysisBundle.message("preview.cannot.perform.action")).br().append(message.messageText()).toFragment(), IntentionPreviewInfo.InfoKind.ERROR);
        }
      }
      else if (command instanceof ModCopyToClipboard copy) {
        navigateInfo = new IntentionPreviewInfo.Html(HtmlChunk.text(
          AnalysisBundle.message("preview.copy.to.clipboard", StringUtil.shortenTextWithEllipsis(copy.content(), 50, 10))));
      }
      else if (command instanceof ModUpdateInspectionOptions options) {
        navigateInfo = new IntentionPreviewInfo.Html(createOptionsPreview(context, options));
      }
    }
    return customDiffList.isEmpty() ? navigateInfo :
           customDiffList.size() == 1 ? customDiffList.get(0) :
           new IntentionPreviewInfo.MultiFileDiff(customDiffList);
  }

  private static @NotNull IntentionPreviewInfo getChoosePreview(@NotNull ActionContext context, @NotNull ModChooseAction target) {
    return target.actions().stream()
      .filter(action -> action.getPresentation(context) != null)
      .findFirst()
      .map(action -> action.generatePreview(context))
      .orElse(IntentionPreviewInfo.EMPTY);
  }

  private static @NotNull HtmlChunk createOptionsPreview(@NotNull ActionContext context, @NotNull ModUpdateInspectionOptions options) {
    InspectionToolWrapper<?, ?> tool =
      InspectionProfileManager.getInstance(context.project()).getCurrentProfile().getInspectionTool(options.inspectionShortName(), context.file());
    if (tool == null) {
      return HtmlChunk.empty();
    }
    HtmlBuilder builder = new HtmlBuilder();
    for (var option : options.options()) {
      builder.append(createOptionPreview(tool.getTool(), option));
    }
    return builder.toFragment();
  }

  private static @NotNull HtmlChunk createOptionPreview(@NotNull InspectionProfileEntry inspection,
                                               ModUpdateInspectionOptions.@NotNull ModifiedInspectionOption option) {
    OptPane pane = inspection.getOptionsPane();
    Object newValue = option.newValue();
    if (newValue instanceof Boolean value) {
      OptCheckbox control = ObjectUtils.tryCast(pane.findControl(option.bindId()), OptCheckbox.class);
      if (control == null) return HtmlChunk.empty();
      HtmlChunk label = text(control.label().label());
      HtmlChunk.Element checkbox = tag("input").attr("type", "checkbox").attr("readonly", "true");
      if (value) {
        checkbox = checkbox.attr("checked", "true");
      }
      HtmlChunk info = tag("table")
        .child(tag("tr").children(
          tag("td").child(checkbox),
          tag("td").child(label)
        ));
      return new HtmlBuilder().append(value ? AnalysisBundle.message("set.inspection.option.description.check")
                                       : AnalysisBundle.message("set.inspection.option.description.uncheck"))
          .br().br().append(info).toFragment();
    }
    if (newValue instanceof Integer value) {
      OptNumber control = ObjectUtils.tryCast(pane.findControl(option.bindId()), OptNumber.class);
      if (control == null) return HtmlChunk.empty();
      LocMessage.PrefixSuffix prefixSuffix = control.splitLabel().splitLabel();
      HtmlChunk.Element input = tag("input").attr("type", "text").attr("value", value)
        .attr("size", value.toString().length() + 1).attr("readonly", "true");
      HtmlChunk info = tag("table").child(tag("tr").children(
        tag("td").child(text(prefixSuffix.prefix())),
        tag("td").child(input),
        tag("td").child(text(prefixSuffix.suffix()))
      ));
      return new HtmlBuilder().append(AnalysisBundle.message("set.inspection.option.description.input"))
          .br().br().append(info).br().toFragment();
    }
    if (newValue instanceof List<?> list) {
      OptStringList control = ObjectUtils.tryCast(pane.findControl(option.bindId()), OptStringList.class);
      if (control == null) return HtmlChunk.empty();
      List<?> oldList = (List<?>)option.oldValue();
      //noinspection unchecked
      return IntentionPreviewInfo.addListOption((List<String>)list, control.label().label(), value -> !oldList.contains(value)).content();
      
    }
    throw new IllegalStateException("Value of type " + newValue.getClass() + " is not supported");
  }
}
