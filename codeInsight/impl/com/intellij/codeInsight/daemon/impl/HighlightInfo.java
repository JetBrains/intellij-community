package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class HighlightInfo {
  public static final HighlightInfo[] EMPTY_ARRAY = new HighlightInfo[0];
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.HighlightInfo");
  private Boolean myNeedsUpdateOnTyping = null;
  private static final Key<HighlightInfoFilter[]> FILTERS = new Key<HighlightInfoFilter[]>("HighlightInfoFilter[]");
  public JComponent fileLevelComponent;

  public HighlightSeverity getSeverity() {
    return severity;
  }

  private TextAttributes forcedTextAttributes;

  public TextAttributes getTextAttributes() {
    return forcedTextAttributes == null ? getAttributesByType(type) : forcedTextAttributes;
  }
  public static TextAttributes getAttributesByType(HighlightInfoType type) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributesKey key = type.getAttributesKey();
    return scheme.getAttributes(key);
  }

  public Color getErrorStripeMarkColor() {
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
    TextAttributes attributes = getAttributesByType(type);
    return attributes == null ? null : attributes.getErrorStripeColor();
  }

  public static HighlightInfo createHighlightInfo(HighlightInfoType type, @NotNull PsiElement element, String description) {
    return createHighlightInfo(type, element, description, htmlEscapeToolTip(description));
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String htmlEscapeToolTip(String description) {
    return description == null ? null : "<html><body>"+XmlUtil.escapeString(description)+"</body></html>";
  }

  public static HighlightInfo createHighlightInfo(HighlightInfoType type, @NotNull PsiElement element, String description, String toolTip) {
    TextRange range = element.getTextRange();
    int start = range.getStartOffset();
    int end = range.getEndOffset();
    return createHighlightInfo(type, start, end, description, toolTip);
  }

  public static HighlightInfo createHighlightInfo(HighlightInfoType type, @NotNull PsiElement element, String description, String toolTip, boolean isEndOfLine) {
    if (isEndOfLine){
      TextRange range = element.getTextRange();
      int end = range.getEndOffset();
      final HighlightInfo highlightInfo = createHighlightInfo(type, end - 1, end, description, toolTip);
      highlightInfo.isAfterEndOfLine = true;
      return highlightInfo;
    } else {
      return createHighlightInfo(type, element, description, toolTip);
    }
  }


  public static HighlightInfo createHighlightInfo(HighlightInfoType type, int start, int end, String description, String toolTip) {
    HighlightInfoFilter[] filters = getFilters();
    HighlightInfo highlightInfo = new HighlightInfo(type, start, end, description, toolTip);
    for (HighlightInfoFilter filter : filters) {
      if (!filter.accept(highlightInfo, null)) {
        return null;
      }
    }
    return highlightInfo;
  }

  private static HighlightInfoFilter[] getFilters() {
    final Application app = ApplicationManager.getApplication();
    HighlightInfoFilter[] filters = app.getUserData(FILTERS);
    if (filters == null) {
      filters = app.getComponents(HighlightInfoFilter.class);
      app.putUserData(FILTERS, filters);
    }
    return filters;
  }

  public static HighlightInfo createHighlightInfo(HighlightInfoType type, int start, int end, String description) {
    return createHighlightInfo(type, start, end, description, htmlEscapeToolTip(description));
  }

  public static HighlightInfo createHighlightInfo(HighlightInfoType type, TextRange textRange, String description) {
    return createHighlightInfo(type, textRange.getStartOffset(), textRange.getEndOffset(), description);
  }
  public static HighlightInfo createHighlightInfo(HighlightInfoType type, TextRange textRange, String description, String toolTip) {
    return createHighlightInfo(type, textRange.getStartOffset(), textRange.getEndOffset(), description, toolTip);
  }
  public static HighlightInfo createHighlightInfo(HighlightInfoType type, TextRange textRange, String description, TextAttributes textAttributes) {
    // do not use HighlightInfoFilter
    HighlightInfo highlightInfo = new HighlightInfo(type, textRange.getStartOffset(), textRange.getEndOffset(), description, htmlEscapeToolTip(description));
    highlightInfo.forcedTextAttributes = textAttributes;
    return highlightInfo;
  }

  public boolean needUpdateOnTyping() {
    if (myNeedsUpdateOnTyping != null) return myNeedsUpdateOnTyping.booleanValue();

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

  public HighlightInfoType type;
  public int group;
  public final int startOffset;
  public final int endOffset;

  public int fixStartOffset;
  public int fixEndOffset;
  public RangeMarker fixMarker;

  public String description;
  public String toolTip;
  public final HighlightSeverity severity;

  public boolean isAfterEndOfLine = false;
  public boolean isFileLevelAnnotation = false; 
  public int navigationShift = 0;

  public RangeHighlighter highlighter;
  public String text;

  public List<Pair<IntentionActionDescriptor, TextRange>> quickFixActionRanges;
  public List<Pair<IntentionActionDescriptor, RangeMarker>> quickFixActionMarkers;

  private GutterIconRenderer gutterIconRenderer;

  public HighlightInfo(HighlightInfoType type, int startOffset, int endOffset, String description, String toolTip) {
    this.type = type;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    fixStartOffset = startOffset;
    fixEndOffset = endOffset;
    this.description = description;
    severity = type.getSeverity(null);
    this.toolTip = toolTip;
    LOG.assertTrue(startOffset >= 0);
    LOG.assertTrue(startOffset <= endOffset);
  }

  public HighlightInfo(TextAttributes textAttributes,
                       HighlightInfoType type,
                       int startOffset,
                       int endOffset,
                       String description,
                       String toolTip,
                       HighlightSeverity severity,
                       boolean afterEndOfLine,
                       boolean needsUpdateOnTyping) {
    forcedTextAttributes = textAttributes;
    this.type = type;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.description = description;
    this.toolTip = toolTip;
    this.severity = severity;
    isAfterEndOfLine = afterEndOfLine;
    myNeedsUpdateOnTyping = needsUpdateOnTyping;
  }

  public boolean equals(Object obj) {
    return obj == this ||
           (obj instanceof HighlightInfo &&
            ((HighlightInfo)obj).getSeverity() == getSeverity() &&
            ((HighlightInfo)obj).startOffset == startOffset &&
            ((HighlightInfo)obj).endOffset == endOffset &&
            ((HighlightInfo)obj).type == type &&
            //Do not include fix offsets!!!
            Comparing.strEqual(((HighlightInfo)obj).description, description)
           );
  }

  public int hashCode() {
    return startOffset;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "HighlightInfo(" +
           "text='" + text + "'" +
           ", description='" + description + "'" +
           ", toolTip='" + toolTip + "'" +
           ")";
  }

  public static HighlightInfo createHighlightInfo(HighlightInfoType type, ASTNode childByRole, String localizedMessage) {
    return createHighlightInfo(type, SourceTreeToPsiMap.treeElementToPsi(childByRole), localizedMessage);
  }

  public GutterIconRenderer getGutterIconRenderer() {
    return gutterIconRenderer;
  }

  public void setGutterIconRenderer(final GutterIconRenderer gutterIconRenderer) {
    this.gutterIconRenderer = gutterIconRenderer;
  }

  public static HighlightInfo createHighlightInfo(final HighlightInfoType type,
                                                  final PsiElement element,
                                                  final String message,
                                                  final TextAttributes attributes) {
    TextRange textRange = element.getTextRange();
    // do not use HighlightInfoFilter
    TextAttributes textAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(type.getAttributesKey());
    HighlightInfo highlightInfo = new HighlightInfo(textAttributes, type, textRange.getStartOffset(), textRange.getEndOffset(), message, htmlEscapeToolTip(message), type.getSeverity(element), false, false);
    highlightInfo.forcedTextAttributes = attributes;
    return highlightInfo;
  }

  public static class IntentionActionDescriptor {
    private IntentionAction myAction;
    private List<IntentionAction> myOptions;
    private String myDisplayName;


    public IntentionActionDescriptor(final IntentionAction action, final List<IntentionAction> options, final String displayName) {
      myAction = action;
      myOptions = options;
      myDisplayName = displayName;
    }


    public IntentionAction getAction() {
      return myAction;
    }

    public List<IntentionAction> getOptions() {
      return myOptions;
    }

    public String getDisplayName() {
      return myDisplayName;
    }
  }
}