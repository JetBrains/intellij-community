/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.actions.CleanupInspectionIntention;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class HighlightInfo implements Segment {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.HighlightInfo");

  public static final HighlightInfo[] EMPTY_ARRAY = new HighlightInfo[0];
  private final boolean myNeedsUpdateOnTyping;
  public JComponent fileLevelComponent;
  public final TextAttributes forcedTextAttributes;

  public HighlightSeverity getSeverity() {
    return severity;
  }

  public TextAttributes getTextAttributes(final PsiElement element) {
    return forcedTextAttributes == null ? getAttributesByType(element, type) : forcedTextAttributes;
  }

  public static TextAttributes getAttributesByType(@Nullable final PsiElement element, @NotNull HighlightInfoType type) {
    final TextAttributes textAttributes = SeverityRegistrar.getInstance(element != null ? element.getProject() : null).getTextAttributesBySeverity(type.getSeverity(element));
    if (textAttributes != null) {
      return textAttributes;
    }
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributesKey key = type.getAttributesKey();
    return scheme.getAttributes(key);
  }

  @Nullable
  public Color getErrorStripeMarkColor(final PsiElement element) {
    if (forcedTextAttributes != null && forcedTextAttributes.getErrorStripeColor() != null) {
      return forcedTextAttributes.getErrorStripeColor();
    }
    if (severity == HighlightSeverity.ERROR) {
      return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES).getErrorStripeColor();
    }
    if (severity == HighlightSeverity.WARNING) {
      return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.WARNINGS_ATTRIBUTES).getErrorStripeColor();
    }
    if (severity == HighlightSeverity.INFO){
      return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.INFO_ATTRIBUTES).getErrorStripeColor();
    }
    if (severity == HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING) {
      return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING).getErrorStripeColor();
    }

    TextAttributes attributes = getAttributesByType(element, type);
    return attributes == null ? null : attributes.getErrorStripeColor();

  }

  public static HighlightInfo createHighlightInfo(@NotNull HighlightInfoType type, @NotNull PsiElement element, String description) {
    return createHighlightInfo(type, element, description, htmlEscapeToolTip(description));
  }

  @Nullable
  @NonNls
  public static String htmlEscapeToolTip(String description) {
    return description == null ? null : "<html><body>"+ XmlStringUtil.escapeString(description)+"</body></html>";
  }

  public static HighlightInfo createHighlightInfo(@NotNull HighlightInfoType type, @NotNull PsiElement element, String description, String toolTip) {
    TextRange range = element.getTextRange();
    int start = range.getStartOffset();
    int end = range.getEndOffset();
    return createHighlightInfo(type, element, start, end, description, toolTip);
  }

  public static HighlightInfo createHighlightInfo(@NotNull HighlightInfoType type, @Nullable PsiElement element, int start, int end, String description,
                                                  String toolTip,
                                                  boolean isEndOfLine,
                                                  TextAttributes forcedAttributes) {
    LOG.assertTrue(ArrayUtil.find(HighlightSeverity.DEFAULT_SEVERITIES, type.getSeverity(element)) != -1 || element != null, "Custom type demands element to detect text attributes");
    HighlightInfo highlightInfo = new HighlightInfo(forcedAttributes, type, start, end, description, toolTip, type.getSeverity(null), isEndOfLine, null, false);
    PsiFile file = element == null ? null : element.getContainingFile();
    for (HighlightInfoFilter filter : getFilters()) {
      if (!filter.accept(highlightInfo, file)) {
        return null;
      }
    }
    return highlightInfo;
  }
  public static HighlightInfo createHighlightInfo(@NotNull HighlightInfoType type, @Nullable PsiElement element, int start, int end, String description, String toolTip) {
    return createHighlightInfo(type, element, start, end, description, toolTip, false, null);
  }

  @NotNull private static HighlightInfoFilter[] getFilters() {
    return ApplicationManager.getApplication().getExtensions(HighlightInfoFilter.EXTENSION_POINT_NAME);
  }

  public static HighlightInfo createHighlightInfo(@NotNull HighlightInfoType type, int start, int end, String description) {
    return createHighlightInfo(type, null, start, end, description, htmlEscapeToolTip(description));
  }

  public static HighlightInfo createHighlightInfo(@NotNull HighlightInfoType type, @NotNull TextRange textRange, String description) {
    return createHighlightInfo(type, textRange.getStartOffset(), textRange.getEndOffset(), description);
  }

  public static HighlightInfo createHighlightInfo(@NotNull HighlightInfoType type, @NotNull TextRange textRange, String description, String toolTip, TextAttributes textAttributes) {
    // do not use HighlightInfoFilter
    return new HighlightInfo(textAttributes, type, textRange.getStartOffset(), textRange.getEndOffset(), description, htmlEscapeToolTip(toolTip),type.getSeverity(null), false, null,
                             false);
  }

  public boolean needUpdateOnTyping() {
    return myNeedsUpdateOnTyping;
  }

  public final HighlightInfoType type;
  public int group;
  public final int startOffset;
  public final int endOffset;

  public int fixStartOffset;
  public int fixEndOffset;
  public RangeMarker fixMarker;

  public final String description;
  public final String toolTip;
  public final HighlightSeverity severity;

  public final boolean isAfterEndOfLine;
  public final boolean isFileLevelAnnotation;
  public int navigationShift = 0;

  public RangeHighlighterEx highlighter;
  public String text;

  public List<Pair<IntentionActionDescriptor, TextRange>> quickFixActionRanges;
  public List<Pair<IntentionActionDescriptor, RangeMarker>> quickFixActionMarkers;
  private boolean hasHint;

  private GutterIconRenderer gutterIconRenderer;

  public HighlightInfo(HighlightInfoType type, int startOffset, int endOffset, String description, String toolTip) {
    this(null, type, startOffset, endOffset, description, toolTip, type.getSeverity(null), false, null, false);
  }

  public HighlightInfo(@Nullable TextAttributes textAttributes,
                       @NotNull HighlightInfoType type,
                       int startOffset,
                       int endOffset,
                       @Nullable String description,
                       @Nullable String toolTip,
                       @NotNull HighlightSeverity severity,
                       boolean afterEndOfLine,
                       @Nullable Boolean needsUpdateOnTyping, boolean isFileLevelAnnotation) {
    if (startOffset < 0 || startOffset > endOffset) {
      LOG.error("Incorrect highlightInfo bounds. description="+description+"; startOffset="+startOffset+"; endOffset="+endOffset+";type="+type);
    }
    forcedTextAttributes = textAttributes;
    this.type = type;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    fixStartOffset = startOffset;
    fixEndOffset = endOffset;
    this.description = description;
    this.toolTip = toolTip;
    this.severity = severity;
    isAfterEndOfLine = afterEndOfLine;
    myNeedsUpdateOnTyping = calcNeedUpdateOnTyping(needsUpdateOnTyping, type);
    this.isFileLevelAnnotation = isFileLevelAnnotation;
  }

  private static boolean calcNeedUpdateOnTyping(Boolean needsUpdateOnTyping, HighlightInfoType type) {
    if (needsUpdateOnTyping != null) return needsUpdateOnTyping.booleanValue();

    if (type == HighlightInfoType.TODO) return false;
    if (type == HighlightInfoType.LOCAL_VARIABLE) return false;
    if (type == HighlightInfoType.INSTANCE_FIELD) return false;
    if (type == HighlightInfoType.STATIC_FIELD) return false;
    if (type == HighlightInfoType.PARAMETER) return false;
    if (type == HighlightInfoType.METHOD_CALL) return false;
    if (type == HighlightInfoType.METHOD_DECLARATION) return false;
    if (type == HighlightInfoType.STATIC_METHOD) return false;
    if (type == HighlightInfoType.CONSTRUCTOR_CALL) return false;
    if (type == HighlightInfoType.CONSTRUCTOR_DECLARATION) return false;
    if (type == HighlightInfoType.INTERFACE_NAME) return false;
    if (type == HighlightInfoType.ABSTRACT_CLASS_NAME) return false;
    if (type == HighlightInfoType.CLASS_NAME) return false;
    return true;
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof HighlightInfo)) return false;
    HighlightInfo info = (HighlightInfo)obj;

    return info.getSeverity() == getSeverity() &&
           info.startOffset == startOffset &&
           info.endOffset == endOffset &&
           Comparing.equal(info.type, type) &&
           Comparing.equal(info.gutterIconRenderer, gutterIconRenderer) &&
           Comparing.equal(info.forcedTextAttributes, forcedTextAttributes) &&
           Comparing.strEqual(info.description, description);
  }

  public boolean equalsByActualOffset(HighlightInfo info) {
    if (info == this) return true;

    return info.getSeverity() == getSeverity() &&
           info.getActualStartOffset() == getActualStartOffset() &&
           info.getActualEndOffset() == getActualEndOffset() &&
           Comparing.equal(info.type, type) &&
           Comparing.equal(info.gutterIconRenderer, gutterIconRenderer) &&
           Comparing.equal(info.forcedTextAttributes, forcedTextAttributes) &&
           Comparing.strEqual(info.description, description);
  }

  public int hashCode() {
    return startOffset;
  }

  @NonNls
  public String toString() {
    @NonNls String s = "HighlightInfo(" + startOffset + "," + endOffset+")";
    if (getActualStartOffset() != startOffset || getActualEndOffset() != endOffset) {
      s += "; actual: (" + getActualStartOffset() + "," + getActualEndOffset() + ")";
    }
    if (text != null) s += " text='" + text + "'";
    if (description != null) s+= ", description='" + description + "'";
    if (toolTip != null) s+= ", toolTip='" + toolTip + "'";
    s += " severity=" + severity;
    return s;
  }

  public static HighlightInfo createHighlightInfo(@NotNull HighlightInfoType type, @NotNull ASTNode childByRole, String localizedMessage) {
    return createHighlightInfo(type, SourceTreeToPsiMap.treeElementToPsi(childByRole), localizedMessage);
  }

  public GutterIconRenderer getGutterIconRenderer() {
    return gutterIconRenderer;
  }

  public void setGutterIconRenderer(final GutterIconRenderer gutterIconRenderer) {
    this.gutterIconRenderer = gutterIconRenderer;
  }

  public static HighlightInfo createHighlightInfo(@NotNull final HighlightInfoType type,
                                                  @NotNull final PsiElement element,
                                                  final String message,
                                                  final TextAttributes attributes) {
    TextRange textRange = element.getTextRange();
    // do not use HighlightInfoFilter
    return new HighlightInfo(attributes, type, textRange.getStartOffset(), textRange.getEndOffset(), message, htmlEscapeToolTip(message),
                             type.getSeverity(element), false, Boolean.FALSE, false);
  }



  public static HighlightInfo fromAnnotation(@NotNull Annotation annotation) {
    return fromAnnotation(annotation, null);
  }

  public static HighlightInfo fromAnnotation(@NotNull Annotation annotation, @Nullable TextRange fixedRange) {
    TextAttributes attributes = annotation.getEnforcedTextAttributes();
    if (attributes == null) {
      attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(annotation.getTextAttributes());
    }
    HighlightInfo info = new HighlightInfo(attributes, convertType(annotation),
                                           fixedRange != null? fixedRange.getStartOffset() : annotation.getStartOffset(),
                                           fixedRange != null? fixedRange.getEndOffset() : annotation.getEndOffset(),
                                           annotation.getMessage(), annotation.getTooltip(),
                                           annotation.getSeverity(), annotation.isAfterEndOfLine(), annotation.needsUpdateOnTyping(), annotation.isFileLevelAnnotation());
    info.setGutterIconRenderer(annotation.getGutterIconRenderer());
    List<Annotation.QuickFixInfo> fixes = annotation.getQuickFixes();
    if (fixes != null) {
      for (final Annotation.QuickFixInfo quickFixInfo : fixes) {
        QuickFixAction.registerQuickFixAction(info, fixedRange != null? fixedRange : quickFixInfo.textRange, quickFixInfo.quickFix, quickFixInfo.key);
      }
    }
    return info;
  }

  public static HighlightInfoType convertType(Annotation annotation) {
    ProblemHighlightType type = annotation.getHighlightType();
    if (type == ProblemHighlightType.LIKE_UNUSED_SYMBOL) return HighlightInfoType.UNUSED_SYMBOL;
    if (type == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) return HighlightInfoType.WRONG_REF;
    if (type == ProblemHighlightType.LIKE_DEPRECATED) return HighlightInfoType.DEPRECATED;
    return convertSeverity(annotation.getSeverity());
  }

  public static HighlightInfoType convertSeverity(final HighlightSeverity severity) {
    return severity == HighlightSeverity.ERROR
           ? HighlightInfoType.ERROR
           : severity == HighlightSeverity.WARNING ? HighlightInfoType.WARNING
             : severity == HighlightSeverity.INFO ? HighlightInfoType.INFO : HighlightInfoType.INFORMATION;
  }

  public static ProblemHighlightType convertType(HighlightInfoType infoType) {
    if (infoType == HighlightInfoType.ERROR || infoType == HighlightInfoType.WRONG_REF) return ProblemHighlightType.ERROR;
    if (infoType == HighlightInfoType.WARNING) return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    if (infoType == HighlightInfoType.INFORMATION) return ProblemHighlightType.INFORMATION;
    return ProblemHighlightType.INFO;
  }

  public static ProblemHighlightType convertSeverityToProblemHighlight(HighlightSeverity severity) {
    return severity == HighlightSeverity.ERROR? ProblemHighlightType.ERROR :
           severity == HighlightSeverity.WARNING ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING :
             severity == HighlightSeverity.INFO ? ProblemHighlightType.INFO : ProblemHighlightType.INFORMATION;
  }


  public boolean hasHint() {
    return hasHint;
  }

  public void setHint(final boolean hasHint) {
    this.hasHint = hasHint;
  }

  public int getActualStartOffset() {
    return highlighter == null || !highlighter.isValid() ? startOffset : highlighter.getStartOffset();
  }
  public int getActualEndOffset() {
    return highlighter == null || !highlighter.isValid() ? endOffset : highlighter.getEndOffset();
  }

  public static class IntentionActionDescriptor {
    private final IntentionAction myAction;
    private volatile List<IntentionAction> myOptions;
    private volatile HighlightDisplayKey myKey;
    private final String myDisplayName;
    private final Icon myIcon;

    public IntentionActionDescriptor(@NotNull IntentionAction action, final HighlightDisplayKey key) {
      this(action, null, HighlightDisplayKey.getDisplayNameByKey(key), null);
      myKey = key;
    }

    public IntentionActionDescriptor(@NotNull IntentionAction action, final List<IntentionAction> options, final String displayName) {
      this(action, options, displayName, null);
    }

    public IntentionActionDescriptor(@NotNull IntentionAction action, final Icon icon) {
      this(action, null, null, icon);
    }

    public IntentionActionDescriptor(@NotNull IntentionAction action, final List<IntentionAction> options, final String displayName, Icon icon) {
      this(action, options, displayName, icon, null);
    }

    public IntentionActionDescriptor(@NotNull IntentionAction action, final List<IntentionAction> options, final String displayName, Icon icon, HighlightDisplayKey key) {
      myAction = action;
      myOptions = options;
      myDisplayName = displayName;
      myIcon = icon;
      myKey = key;
    }

    @NotNull
    public IntentionAction getAction() {
      return myAction;
    }

    @Nullable
    public List<IntentionAction> getOptions(@NotNull PsiElement element) {
      Editor editor = PsiUtilBase.findEditor(element);
      if (editor != null && Boolean.FALSE.equals(editor.getUserData(IntentionManager.SHOW_INTENTION_OPTIONS_KEY))) {
        return null;
      }
      List<IntentionAction> options = myOptions;
      HighlightDisplayKey key = myKey;
      if (options != null || key == null) {
        return options;
      }
      List<IntentionAction> newOptions = IntentionManager.getInstance().getStandardIntentionOptions(key, element);
      InspectionProfile profile = InspectionProjectProfileManager.getInstance(element.getProject()).getInspectionProfile();
      InspectionProfileEntry tool = profile.getInspectionTool(key.toString(), element);
      if (!(tool instanceof LocalInspectionToolWrapper)) {
        HighlightDisplayKey idkey = HighlightDisplayKey.findById(key.toString());
        if (idkey != null) {
          tool = profile.getInspectionTool(idkey.toString(), element);
        }
      }
      if (tool instanceof LocalInspectionToolWrapper) {
        final LocalInspectionTool localInspectionTool = ((LocalInspectionToolWrapper)tool).getTool();
        Class aClass = myAction.getClass();
        if (myAction instanceof QuickFixWrapper) {
          aClass = ((QuickFixWrapper)myAction).getFix().getClass();
        }
        newOptions.add(new CleanupInspectionIntention(localInspectionTool, aClass));
        if (localInspectionTool instanceof CustomSuppressableInspectionTool) {
          final IntentionAction[] suppressActions = ((CustomSuppressableInspectionTool)localInspectionTool).getSuppressActions(element);
          if (suppressActions != null) {
            ContainerUtil.addAll(newOptions, suppressActions);
          }
        }
      }

      synchronized (this) {
        options = myOptions;
        if (options == null) {
          myOptions = options = newOptions;
        }
        myKey = null;
      }
      return options;
    }

    public String getDisplayName() {
      return myDisplayName;
    }

    @NonNls
    public String toString() {
      return "descriptor: " + getAction().getText();
    }

    public Icon getIcon() {
      return myIcon;
    }
  }

  @Override
  public int getStartOffset() {
    return getActualStartOffset();
  }

  @Override
  public int getEndOffset() {
    return getActualEndOffset();
  }
}
