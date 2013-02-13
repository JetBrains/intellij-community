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
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
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
import com.intellij.util.ArrayUtilRt;
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
  JComponent fileLevelComponent;
  public final TextAttributes forcedTextAttributes;
  public final TextAttributesKey forcedTextAttributesKey;

  public final HighlightInfoType type;
  int group;
  public final int startOffset;
  public final int endOffset;

  public int fixStartOffset;
  public int fixEndOffset;
  RangeMarker fixMarker; // null means it the same as highlighter

  public final String description;
  public final String toolTip;
  private final HighlightSeverity severity;

  public final boolean isAfterEndOfLine;
  public final boolean isFileLevelAnnotation;
  final int navigationShift;

  RangeHighlighterEx highlighter;
  public String text;

  public List<Pair<IntentionActionDescriptor, TextRange>> quickFixActionRanges;
  public List<Pair<IntentionActionDescriptor, RangeMarker>> quickFixActionMarkers;
  private boolean hasHint;
  boolean fromInjection;

  private GutterIconRenderer gutterIconRenderer;
  private String myProblemGroup;
  volatile boolean bijective;

  public HighlightSeverity getSeverity() {
    return severity;
  }

  @Nullable
  public TextAttributes getTextAttributes(@Nullable final PsiElement element, @Nullable final EditorColorsScheme editorColorsScheme) {
    if (forcedTextAttributes != null) {
      return forcedTextAttributes;
    }

    final EditorColorsScheme colorsScheme = getColorsScheme(editorColorsScheme);
    if (colorsScheme == null) {
      return null;
    }

    if (forcedTextAttributesKey != null) {
      return colorsScheme.getAttributes(forcedTextAttributesKey);
    }

    return getAttributesByType(element, type, colorsScheme);
  }

  public static TextAttributes getAttributesByType(@Nullable final PsiElement element,
                                                   @NotNull HighlightInfoType type,
                                                   @NotNull EditorColorsScheme colorsScheme) {
    final SeverityRegistrar severityRegistrar = SeverityRegistrar.getInstance(element != null ? element.getProject() : null);
    final TextAttributes textAttributes = severityRegistrar.getTextAttributesBySeverity(type.getSeverity(element));
    if (textAttributes != null) {
      return textAttributes;
    }
    TextAttributesKey key = type.getAttributesKey();
    return colorsScheme.getAttributes(key);
  }

  @Nullable
  public Color getErrorStripeMarkColor(@NotNull PsiElement element,
                                       @Nullable final EditorColorsScheme colorsScheme) { // if null global scheme will be used
    if (forcedTextAttributes != null && forcedTextAttributes.getErrorStripeColor() != null) {
      return forcedTextAttributes.getErrorStripeColor();
    }
    final EditorColorsScheme scheme = getColorsScheme(colorsScheme);
    if (scheme == null) {
      return null;
    }
    if (forcedTextAttributesKey != null) {
      TextAttributes forcedTextAttributes = scheme.getAttributes(forcedTextAttributesKey);
      if (forcedTextAttributes != null) {
        final Color errorStripeColor = forcedTextAttributes.getErrorStripeColor();
        // let's copy above behaviour of forcedTextAttributes stripe color, but I'm not sure that the behaviour is correct in general
        if (errorStripeColor != null) {
          return errorStripeColor;
        }
      }
    }

    if (getSeverity() == HighlightSeverity.ERROR) {
      return scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES).getErrorStripeColor();
    }
    if (getSeverity() == HighlightSeverity.WARNING) {
      return scheme.getAttributes(CodeInsightColors.WARNINGS_ATTRIBUTES).getErrorStripeColor();
    }
    if (getSeverity() == HighlightSeverity.INFO){
      return scheme.getAttributes(CodeInsightColors.INFO_ATTRIBUTES).getErrorStripeColor();
    }
    if (getSeverity() == HighlightSeverity.WEAK_WARNING){
      return scheme.getAttributes(CodeInsightColors.WEAK_WARNING_ATTRIBUTES).getErrorStripeColor();
    }
    if (getSeverity() == HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING) {
      return scheme.getAttributes(CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING).getErrorStripeColor();
    }

    TextAttributes attributes = getAttributesByType(element, type, scheme);
    return attributes == null ? null : attributes.getErrorStripeColor();

  }

  @Nullable
  private static EditorColorsScheme getColorsScheme(@Nullable final EditorColorsScheme customScheme) {
    if (customScheme != null) {
      return customScheme;
    }
    return EditorColorsManager.getInstance().getGlobalScheme();
  }

  @Nullable
  @NonNls
  private static String htmlEscapeToolTip(@Nullable String unescapedTooltip) {
    return unescapedTooltip == null ? null : "<html><body>"+ XmlStringUtil.escapeString(unescapedTooltip)+"</body></html>";
  }

  @NotNull
  private static HighlightInfoFilter[] getFilters() {
    return ApplicationManager.getApplication().getExtensions(HighlightInfoFilter.EXTENSION_POINT_NAME);
  }

  public boolean needUpdateOnTyping() {
    return myNeedsUpdateOnTyping;
  }

  HighlightInfo(@NotNull HighlightInfoType type, int startOffset, int endOffset, String escapedDescription, String escapedToolTip) {
    this(null, null, type, startOffset, endOffset, escapedDescription, escapedToolTip, type.getSeverity(null), false, null, false, 0);
  }

  //primary
  HighlightInfo(@Nullable TextAttributes forcedTextAttributes,
                @Nullable TextAttributesKey forcedTextAttributesKey,
                @NotNull HighlightInfoType type,
                int startOffset,
                int endOffset,
                @Nullable String escapedDescription,
                @Nullable String escapedToolTip,
                @NotNull HighlightSeverity severity,
                boolean afterEndOfLine,
                @Nullable Boolean needsUpdateOnTyping,
                boolean isFileLevelAnnotation,
                int navigationShift) {
    if (startOffset < 0 || startOffset > endOffset) {
      LOG.error("Incorrect highlightInfo bounds. description="+escapedDescription+"; startOffset="+startOffset+"; endOffset="+endOffset+";type="+type);
    }
    this.forcedTextAttributes = forcedTextAttributes;
    this.forcedTextAttributesKey = forcedTextAttributesKey;
    this.type = type;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    fixStartOffset = startOffset;
    fixEndOffset = endOffset;
    description = escapedDescription;
    toolTip = escapedToolTip;
    this.severity = severity;
    isAfterEndOfLine = afterEndOfLine;
    myNeedsUpdateOnTyping = calcNeedUpdateOnTyping(needsUpdateOnTyping, type);
    this.isFileLevelAnnotation = isFileLevelAnnotation;
    this.navigationShift = navigationShift;
  }

  private static boolean calcNeedUpdateOnTyping(@Nullable Boolean needsUpdateOnTyping, HighlightInfoType type) {
    if (needsUpdateOnTyping != null) return needsUpdateOnTyping.booleanValue();

    if (type == HighlightInfoType.TODO) return false;
    if (type == HighlightInfoType.LOCAL_VARIABLE) return false;
    if (type == HighlightInfoType.INSTANCE_FIELD) return false;
    if (type == HighlightInfoType.STATIC_FIELD) return false;
    if (type == HighlightInfoType.STATIC_FINAL_FIELD) return false;
    if (type == HighlightInfoType.PARAMETER) return false;
    if (type == HighlightInfoType.METHOD_CALL) return false;
    if (type == HighlightInfoType.METHOD_DECLARATION) return false;
    if (type == HighlightInfoType.STATIC_METHOD) return false;
    if (type == HighlightInfoType.ABSTRACT_METHOD) return false;
    if (type == HighlightInfoType.INHERITED_METHOD) return false;
    if (type == HighlightInfoType.CONSTRUCTOR_CALL) return false;
    if (type == HighlightInfoType.CONSTRUCTOR_DECLARATION) return false;
    if (type == HighlightInfoType.INTERFACE_NAME) return false;
    if (type == HighlightInfoType.ABSTRACT_CLASS_NAME) return false;
    if (type == HighlightInfoType.ENUM_NAME) return false;
    if (type == HighlightInfoType.CLASS_NAME) return false;
    if (type == HighlightInfoType.ANONYMOUS_CLASS_NAME) return false;
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
           Comparing.equal(info.forcedTextAttributesKey, forcedTextAttributesKey) &&
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
           Comparing.equal(info.forcedTextAttributesKey, forcedTextAttributesKey) &&
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
    s += " severity=" + getSeverity();
    s += " group=" + group;

    if (quickFixActionRanges != null) {
      s+= "; quickFixes: "+quickFixActionRanges;
    }
    if (gutterIconRenderer != null) {
      s += "; gutter: " + gutterIconRenderer;
    }
    return s;
  }

  @NotNull
  public static Builder newHighlightInfo(@NotNull HighlightInfoType type) {
    return new B(type);
  }
  public interface Builder {
    // only one allowed
    @NotNull Builder range(@NotNull TextRange textRange);
    @NotNull Builder range(@NotNull ASTNode node);
    @NotNull Builder range(@NotNull PsiElement element);
    @NotNull Builder range(@NotNull PsiElement element, int start, int end);
    @NotNull Builder range(int start, int end);

    @NotNull Builder gutterIconRenderer(@NotNull GutterIconRenderer gutterIconRenderer);
    @NotNull Builder problemGroup(@NotNull String problemGroup);

    // only one allowed
    @NotNull Builder description(@NotNull String description);
    @NotNull Builder descriptionAndTooltip(@NotNull String description);

    // only one allowed
    @NotNull Builder textAttributes(@NotNull TextAttributes attributes);
    @NotNull Builder textAttributes(@NotNull TextAttributesKey attributesKey);

    // only one allowed
    @NotNull Builder unescapedToolTip(@NotNull String unescapedToolTip);
    @NotNull Builder escapedToolTip(@NotNull String escapedToolTip);

    @NotNull Builder endOfLine();
    @NotNull Builder needsUpdateOnTyping(boolean update);
    @NotNull Builder severity(@NotNull HighlightSeverity severity);
    @NotNull Builder fileLevelAnnotation();
    @NotNull Builder navigationShift(int navigationShift);

    @Nullable("null means filtered out")
    HighlightInfo create();

    @NotNull
    HighlightInfo createUnconditionally();
  }

  private static class B implements Builder {
    private Boolean myNeedsUpdateOnTyping;
    private TextAttributes forcedTextAttributes;
    private TextAttributesKey forcedTextAttributesKey;

    private final HighlightInfoType type;
    private int startOffset = -1;
    private int endOffset = -1;

    private String escapedDescription;
    private String escapedToolTip;
    private HighlightSeverity severity;

    private boolean isAfterEndOfLine;
    private boolean isFileLevelAnnotation;
    private int navigationShift = 0;

    private GutterIconRenderer gutterIconRenderer;
    private String problemGroup;
    private PsiElement psiElement;

    public B(@NotNull HighlightInfoType type) {
      this.type = type;
    }

    @NotNull
    @Override
    public Builder gutterIconRenderer(@NotNull GutterIconRenderer gutterIconRenderer) {
      assert this.gutterIconRenderer == null : "gutterIconRenderer already set";
      this.gutterIconRenderer = gutterIconRenderer;
      return this;
    }

    @NotNull
    @Override
    public Builder problemGroup(@NotNull String problemGroup) {
      assert this.problemGroup == null : "problemGroup already set";
      this.problemGroup = problemGroup;
      return this;
    }

    @NotNull
    @Override
    public Builder description(@NotNull String description) {
      assert escapedDescription == null : "description already set";
      escapedDescription = description;
      return this;
    }

    @NotNull
    @Override
    public Builder descriptionAndTooltip(@NotNull String description) {
      return description(description).unescapedToolTip(description);
    }

    @NotNull
    @Override
    public Builder textAttributes(@NotNull TextAttributes attributes) {
      assert forcedTextAttributes == null : "textattributes already set";
      forcedTextAttributes = attributes;
      return this;
    }

    @NotNull
    @Override
    public Builder textAttributes(@NotNull TextAttributesKey attributesKey) {
      assert forcedTextAttributesKey == null : "textattributesKey already set";
      forcedTextAttributesKey = attributesKey;
      return this;
    }

    @NotNull
    @Override
    public Builder unescapedToolTip(@NotNull String unescapedToolTip) {
      assert escapedToolTip == null : "Tooltip was already set";
      escapedToolTip = htmlEscapeToolTip(unescapedToolTip);
      return this;
    }

    @NotNull
    @Override
    public Builder escapedToolTip(@NotNull String escapedToolTip) {
      assert this.escapedToolTip == null : "Tooltip was already set";
      this.escapedToolTip = escapedToolTip;
      return this;
    }

    @NotNull
    @Override
    public Builder range(int start, int end) {
      assert startOffset == -1 && endOffset == -1 : "Offsets already set";

      startOffset = start;
      endOffset = end;
      return this;
    }

    @NotNull
    @Override
    public Builder range(@NotNull TextRange textRange) {
      assert startOffset == -1 && endOffset == -1 : "Offsets already set";
      startOffset = textRange.getStartOffset();
      endOffset = textRange.getEndOffset();
      return this;
    }

    @NotNull
    @Override
    public Builder range(@NotNull ASTNode node) {
      return range(node.getPsi());
    }

    @NotNull
    @Override
    public Builder range(@NotNull PsiElement element) {
      assert psiElement == null : " psiElement already set";
      psiElement = element;
      return range(element.getTextRange());
    }

    @NotNull
    @Override
    public Builder range(@NotNull PsiElement element, int start, int end) {
      assert psiElement == null : " psiElement already set";
      psiElement = element;
      return range(start, end);
    }

    @NotNull
    @Override
    public Builder endOfLine() {
      isAfterEndOfLine = true;
      return this;
    }

    @NotNull
    @Override
    public Builder needsUpdateOnTyping(boolean update) {
      assert myNeedsUpdateOnTyping == null : " needsUpdateOnTyping already set";
      myNeedsUpdateOnTyping = update;
      return this;
    }

    @NotNull
    @Override
    public Builder severity(@NotNull HighlightSeverity severity) {
      assert this.severity == null : " severity already set";
      this.severity = severity;
      return this;
    }

    @NotNull
    @Override
    public Builder fileLevelAnnotation() {
      isFileLevelAnnotation = true;
      return this;
    }

    @NotNull
    @Override
    public Builder navigationShift(int navigationShift) {
      this.navigationShift = navigationShift;
      return this;
    }

    @Nullable
    @Override
    public HighlightInfo create() {
      HighlightInfo info = createUnconditionally();
      LOG.assertTrue(psiElement != null || severity == HighlightInfoType.SYMBOL_TYPE_SEVERITY || severity == HighlightInfoType.INJECTED_FRAGMENT_SEVERITY || ArrayUtilRt.find(HighlightSeverity.DEFAULT_SEVERITIES, severity) != -1,
                     "Custom type demands element to detect its text attributes");

      PsiFile file = psiElement == null ? null : psiElement.getContainingFile();
      for (HighlightInfoFilter filter : getFilters()) {
        if (!filter.accept(info, file)) {
          return null;
        }
      }

      return info;
    }

    @NotNull
    @Override
    public HighlightInfo createUnconditionally() {
      if (severity == null) {
        severity = type.getSeverity(psiElement);
      }

      return new HighlightInfo(forcedTextAttributes, forcedTextAttributesKey, type, startOffset, endOffset, escapedDescription,
                        escapedToolTip, severity, isAfterEndOfLine, myNeedsUpdateOnTyping, isFileLevelAnnotation, navigationShift);
    }
  }

  public GutterIconRenderer getGutterIconRenderer() {
    return gutterIconRenderer;
  }

  public void setGutterIconRenderer(final GutterIconRenderer gutterIconRenderer) {
    this.gutterIconRenderer = gutterIconRenderer;
  }

  @Nullable
  public String getProblemGroup() {
    return myProblemGroup;
  }

  private void setProblemGroup(@Nullable String problemGroup) {
    myProblemGroup = problemGroup;
  }


  @NotNull
  public static HighlightInfo fromAnnotation(@NotNull Annotation annotation) {
    return fromAnnotation(annotation, null, false);
  }

  @NotNull
  public static HighlightInfo fromAnnotation(@NotNull Annotation annotation, @Nullable TextRange fixedRange, boolean batchMode) {
    final TextAttributes forcedAttributes = annotation.getEnforcedTextAttributes();
    final TextAttributesKey forcedAttributesKey = forcedAttributes == null ? annotation.getTextAttributes() : null;

    HighlightInfo info = new HighlightInfo(forcedAttributes, forcedAttributesKey, convertType(annotation),
                                           fixedRange != null? fixedRange.getStartOffset() : annotation.getStartOffset(),
                                           fixedRange != null? fixedRange.getEndOffset() : annotation.getEndOffset(),
                                           annotation.getMessage(), annotation.getTooltip(),
                                           annotation.getSeverity(), annotation.isAfterEndOfLine(), annotation.needsUpdateOnTyping(), annotation.isFileLevelAnnotation(),
                                           0);
    info.setGutterIconRenderer(annotation.getGutterIconRenderer());
    info.setProblemGroup(annotation.getProblemGroup());
    appendFixes(fixedRange, info, batchMode ? annotation.getBatchFixes() : annotation.getQuickFixes());
    return info;
  }

  private static void appendFixes(@Nullable TextRange fixedRange, HighlightInfo info, List<Annotation.QuickFixInfo> fixes) {
    if (fixes != null) {
      for (final Annotation.QuickFixInfo quickFixInfo : fixes) {
        QuickFixAction.registerQuickFixAction(info, fixedRange != null ? fixedRange : quickFixInfo.textRange, quickFixInfo.quickFix,
                                              quickFixInfo.key != null ? quickFixInfo.key : HighlightDisplayKey.find(DefaultHighlightVisitorBasedInspection.AnnotatorBasedInspection.ANNOTATOR_SHORT_NAME));
      }
    }
  }

  public static HighlightInfoType convertType(Annotation annotation) {
    ProblemHighlightType type = annotation.getHighlightType();
    if (type == ProblemHighlightType.LIKE_UNUSED_SYMBOL) return HighlightInfoType.UNUSED_SYMBOL;
    if (type == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) return HighlightInfoType.WRONG_REF;
    if (type == ProblemHighlightType.LIKE_DEPRECATED) return HighlightInfoType.DEPRECATED;
    return convertSeverity(annotation.getSeverity());
  }

  public static HighlightInfoType convertSeverity(final HighlightSeverity severity) {
    return severity == HighlightSeverity.ERROR? HighlightInfoType.ERROR :
           severity == HighlightSeverity.WARNING ? HighlightInfoType.WARNING :
           severity == HighlightSeverity.INFO ? HighlightInfoType.INFO :
           severity == HighlightSeverity.WEAK_WARNING ? HighlightInfoType.WEAK_WARNING :
           severity ==HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING ? HighlightInfoType.GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER :
           HighlightInfoType.INFORMATION;
  }

  public static ProblemHighlightType convertType(HighlightInfoType infoType) {
    if (infoType == HighlightInfoType.ERROR || infoType == HighlightInfoType.WRONG_REF) return ProblemHighlightType.ERROR;
    if (infoType == HighlightInfoType.WARNING) return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    if (infoType == HighlightInfoType.INFORMATION) return ProblemHighlightType.INFORMATION;
    return ProblemHighlightType.WEAK_WARNING;
  }

  public static ProblemHighlightType convertSeverityToProblemHighlight(HighlightSeverity severity) {
    return severity == HighlightSeverity.ERROR ? ProblemHighlightType.ERROR :
           severity == HighlightSeverity.WARNING ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING :
           severity == HighlightSeverity.INFO ? ProblemHighlightType.INFO :
           severity == HighlightSeverity.WEAK_WARNING ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.INFORMATION;
  }


  public boolean hasHint() {
    return hasHint;
  }

  public void setHint(final boolean hasHint) {
    this.hasHint = hasHint;
  }

  public int getActualStartOffset() {
    RangeHighlighterEx h = highlighter;
    return h == null || !h.isValid() ? startOffset : h.getStartOffset();
  }
  public int getActualEndOffset() {
    RangeHighlighterEx h = highlighter;
    return h == null || !h.isValid() ? endOffset : h.getEndOffset();
  }

  //public void setCustomColorScheme(@Nullable final EditorColorsScheme customColorScheme) {
  //  myCustomColorScheme = customColorScheme;
  //}

  public static class IntentionActionDescriptor {
    private final IntentionAction myAction;
    private volatile List<IntentionAction> myOptions;
    private volatile HighlightDisplayKey myKey;
    private final String myDisplayName;
    private final Icon myIcon;

    public IntentionActionDescriptor(@NotNull IntentionAction action, final List<IntentionAction> options, final String displayName) {
      this(action, options, displayName, null);
    }

    public IntentionActionDescriptor(@NotNull IntentionAction action, final Icon icon) {
      this(action, null, null, icon);
    }

    public IntentionActionDescriptor(@NotNull IntentionAction action, @Nullable final List<IntentionAction> options, @Nullable final String displayName, @Nullable Icon icon) {
      this(action, options, displayName, icon, null);
    }

    public IntentionActionDescriptor(@NotNull IntentionAction action, @Nullable final List<IntentionAction> options, @Nullable final String displayName, @Nullable Icon icon, @Nullable HighlightDisplayKey key) {
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
    public List<IntentionAction> getOptions(@NotNull PsiElement element, @Nullable Editor editor) {
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
      InspectionProfileEntry wrappedTool = tool;
      if (tool instanceof LocalInspectionToolWrapper) {
        wrappedTool = ((LocalInspectionToolWrapper)tool).getTool();
        Class aClass = myAction.getClass();
        if (myAction instanceof QuickFixWrapper) {
          aClass = ((QuickFixWrapper)myAction).getFix().getClass();
        }
        newOptions.add(new CleanupInspectionIntention((LocalInspectionToolWrapper)tool, aClass));
      } else if (tool instanceof GlobalInspectionToolWrapper) {
        wrappedTool = ((GlobalInspectionToolWrapper)tool).getTool();
        if (wrappedTool instanceof GlobalSimpleInspectionTool && (myAction instanceof LocalQuickFix || myAction instanceof QuickFixWrapper)) {
          Class aClass = myAction.getClass();
          if (myAction instanceof QuickFixWrapper) {
            aClass = ((QuickFixWrapper)myAction).getFix().getClass();
          }
          newOptions.add(new CleanupInspectionIntention((GlobalInspectionToolWrapper)tool, aClass));
        }
      }

      if (wrappedTool instanceof CustomSuppressableInspectionTool) {
        final IntentionAction[] suppressActions = ((CustomSuppressableInspectionTool)wrappedTool).getSuppressActions(element);
        if (suppressActions != null) {
          ContainerUtil.addAll(newOptions, suppressActions);
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

    @Nullable
    public String getDisplayName() {
      return myDisplayName;
    }

    @NonNls
    public String toString() {
      String text = getAction().getText();
      return "descriptor: " + (text.isEmpty() ? getAction().getClass() : text);
    }

    @Nullable
    public Icon getIcon() {
      return myIcon;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof IntentionActionDescriptor && myAction.equals(((IntentionActionDescriptor)obj).myAction);
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

  public int getGroup() {
    return group;
  }

  public boolean isFromInjection() {
    return fromInjection;
  }
}
