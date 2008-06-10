package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.FileTypeRegistrator;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.ide.highlighter.custom.impl.CustomFileTypeEditor;
import com.intellij.lang.Commenter;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.ExternalizableFileType;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.ExternalInfo;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class AbstractFileType extends UserFileType<AbstractFileType> implements ExternalizableFileType, ExternalizableScheme {
  protected SyntaxTable mySyntaxTable;
  private SyntaxTable myDefaultSyntaxTable;
  protected Commenter myCommenter = null;
  @NonNls public static final String ELEMENT_HIGHLIGHTING = "highlighting";
  @NonNls private static final String ELEMENT_OPTIONS = "options";
  @NonNls private static final String ELEMENT_OPTION = "option";
  @NonNls private static final String ATTRIBUTE_VALUE = "value";
  @NonNls private static final String VALUE_LINE_COMMENT = "LINE_COMMENT";
  @NonNls private static final String VALUE_COMMENT_START = "COMMENT_START";
  @NonNls private static final String VALUE_COMMENT_END = "COMMENT_END";
  @NonNls private static final String VALUE_HEX_PREFIX = "HEX_PREFIX";
  @NonNls private static final String VALUE_NUM_POSTFIXES = "NUM_POSTFIXES";
  @NonNls private static final String VALUE_HAS_BRACES = "HAS_BRACES";
  @NonNls private static final String VALUE_HAS_BRACKETS = "HAS_BRACKETS";
  @NonNls private static final String VALUE_HAS_PARENS = "HAS_PARENS";
  @NonNls private static final String VALUE_HAS_STRING_ESCAPES = "HAS_STRING_ESCAPES";
  @NonNls private static final String ELEMENT_KEYWORDS = "keywords";
  @NonNls private static final String ATTRIBUTE_IGNORE_CASE = "ignore_case";
  @NonNls private static final String ELEMENT_KEYWORD = "keyword";
  @NonNls private static final String ELEMENT_KEYWORDS2 = "keywords2";
  @NonNls private static final String ELEMENT_KEYWORDS3 = "keywords3";
  @NonNls private static final String ELEMENT_KEYWORDS4 = "keywords4";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls public static final String ELEMENT_EXTENSIONMAP = "extensionMap";
  private ExternalInfo myExternalInfo = new ExternalInfo();

  public AbstractFileType(SyntaxTable syntaxTable) {
    mySyntaxTable = syntaxTable;
  }

  public void initSupport() {
    for (FileTypeRegistrator registrator : Extensions.getRootArea().getExtensionPoint(FileTypeRegistrator.EP_NAME).getExtensions()) {
      registrator.initFileType(this);
    }
  }

  public SyntaxTable getSyntaxTable() {
    return mySyntaxTable;
  }

  public Commenter getCommenter() {
    return myCommenter;
  }

  public void setSyntaxTable(SyntaxTable syntaxTable) {
    mySyntaxTable = syntaxTable;
  }

  public AbstractFileType clone() {
    return (AbstractFileType)super.clone();
  }

  public void copyFrom(UserFileType newType) {
    super.copyFrom(newType);
    if (newType instanceof AbstractFileType) {
      mySyntaxTable = ((AbstractFileType)newType).getSyntaxTable();
      myExternalInfo.copy(((AbstractFileType)newType).myExternalInfo);
    }
  }

  public boolean isBinary() {
    return false;
  }

  public void readExternal(final Element typeElement) throws InvalidDataException {
    Element element = typeElement.getChild(ELEMENT_HIGHLIGHTING);
    if (element != null) {
      SyntaxTable table = readSyntaxTable(element);
      if (table != null) {
        setSyntaxTable(table);
      }
    }
  }

  public static SyntaxTable readSyntaxTable(Element root) {
    SyntaxTable table = new SyntaxTable();

    for (final Object o : root.getChildren()) {
      Element element = (Element)o;

      if (ELEMENT_OPTIONS.equals(element.getName())) {
        for (final Object o1 : element.getChildren(ELEMENT_OPTION)) {
          Element e = (Element)o1;
          String name = e.getAttributeValue(ATTRIBUTE_NAME);
          String value = e.getAttributeValue(ATTRIBUTE_VALUE);
          if (VALUE_LINE_COMMENT.equals(name)) {
            table.setLineComment(value);
          }
          else if (VALUE_COMMENT_START.equals(name)) {
            table.setStartComment(value);
          }
          else if (VALUE_COMMENT_END.equals(name)) {
            table.setEndComment(value);
          }
          else if (VALUE_HEX_PREFIX.equals(name)) {
            table.setHexPrefix(value);
          }
          else if (VALUE_NUM_POSTFIXES.equals(name)) {
            table.setNumPostfixChars(value);
          }
          else if (VALUE_HAS_BRACES.equals(name)) {
            table.setHasBraces(Boolean.valueOf(value).booleanValue());
          }
          else if (VALUE_HAS_BRACKETS.equals(name)) {
            table.setHasBrackets(Boolean.valueOf(value).booleanValue());
          }
          else if (VALUE_HAS_PARENS.equals(name)) {
            table.setHasParens(Boolean.valueOf(value).booleanValue());
          } else if (VALUE_HAS_STRING_ESCAPES.equals(name)) {
            table.setHasStringEscapes(Boolean.valueOf(value).booleanValue());
          }
        }
      }
      else if (ELEMENT_KEYWORDS.equals(element.getName())) {
        boolean ignoreCase = Boolean.valueOf(element.getAttributeValue(ATTRIBUTE_IGNORE_CASE)).booleanValue();
        table.setIgnoreCase(ignoreCase);
        for (final Object o1 : element.getChildren(ELEMENT_KEYWORD)) {
          Element e = (Element)o1;
          table.addKeyword1(e.getAttributeValue(ATTRIBUTE_NAME));
        }
      }
      else if (ELEMENT_KEYWORDS2.equals(element.getName())) {
        for (final Object o1 : element.getChildren(ELEMENT_KEYWORD)) {
          Element e = (Element)o1;
          table.addKeyword2(e.getAttributeValue(ATTRIBUTE_NAME));
        }
      }
      else if (ELEMENT_KEYWORDS3.equals(element.getName())) {
        for (final Object o1 : element.getChildren(ELEMENT_KEYWORD)) {
          Element e = (Element)o1;
          table.addKeyword3(e.getAttributeValue(ATTRIBUTE_NAME));
        }
      }
      else if (ELEMENT_KEYWORDS4.equals(element.getName())) {
        for (final Object o1 : element.getChildren(ELEMENT_KEYWORD)) {
          Element e = (Element)o1;
          table.addKeyword4(e.getAttributeValue(ATTRIBUTE_NAME));
        }
      }
    }

    return table;
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    SyntaxTable table = getSyntaxTable();
    Element highlightingElement = new Element(ELEMENT_HIGHLIGHTING);

    Element optionsElement = new Element(ELEMENT_OPTIONS);

    Element lineComment = new Element(ELEMENT_OPTION);
    lineComment.setAttribute(ATTRIBUTE_NAME, VALUE_LINE_COMMENT);
    lineComment.setAttribute(ATTRIBUTE_VALUE, table.getLineComment());
    optionsElement.addContent(lineComment);

    Element commentStart = new Element(ELEMENT_OPTION);
    commentStart.setAttribute(ATTRIBUTE_NAME, VALUE_COMMENT_START);
    commentStart.setAttribute(ATTRIBUTE_VALUE, table.getStartComment());
    optionsElement.addContent(commentStart);

    Element commentEnd = new Element(ELEMENT_OPTION);
    commentEnd.setAttribute(ATTRIBUTE_NAME, VALUE_COMMENT_END);
    commentEnd.setAttribute(ATTRIBUTE_VALUE, table.getEndComment());
    optionsElement.addContent(commentEnd);

    Element hexPrefix = new Element(ELEMENT_OPTION);
    hexPrefix.setAttribute(ATTRIBUTE_NAME, VALUE_HEX_PREFIX);
    hexPrefix.setAttribute(ATTRIBUTE_VALUE, table.getHexPrefix());
    optionsElement.addContent(hexPrefix);

    Element numPostfixes = new Element(ELEMENT_OPTION);
    numPostfixes.setAttribute(ATTRIBUTE_NAME, VALUE_NUM_POSTFIXES);
    numPostfixes.setAttribute(ATTRIBUTE_VALUE, table.getNumPostfixChars());
    optionsElement.addContent(numPostfixes);

    addElementOption(optionsElement, VALUE_HAS_BRACKETS, table.isHasBrackets());
    addElementOption(optionsElement, VALUE_HAS_BRACES, table.isHasBraces());
    addElementOption(optionsElement, VALUE_HAS_PARENS, table.isHasParens());
    addElementOption(optionsElement, VALUE_HAS_STRING_ESCAPES, table.isHasStringEscapes());

    highlightingElement.addContent(optionsElement);

    Element keywordsElement = new Element(ELEMENT_KEYWORDS);
    keywordsElement.setAttribute(ATTRIBUTE_IGNORE_CASE, String.valueOf(table.isIgnoreCase()));
    writeKeywords(table.getKeywords1(), keywordsElement);
    highlightingElement.addContent(keywordsElement);

    Element keywordsElement2 = new Element(ELEMENT_KEYWORDS2);
    writeKeywords(table.getKeywords2(), keywordsElement2);
    highlightingElement.addContent(keywordsElement2);

    Element keywordsElement3 = new Element(ELEMENT_KEYWORDS3);
    writeKeywords(table.getKeywords3(), keywordsElement3);
    highlightingElement.addContent(keywordsElement3);

    Element keywordsElement4 = new Element(ELEMENT_KEYWORDS4);
    writeKeywords(table.getKeywords4(), keywordsElement4);
    highlightingElement.addContent(keywordsElement4);

    element.addContent(highlightingElement);
  }

  private static void addElementOption(final Element optionsElement, final String valueHasParens, final boolean hasParens) {
    Element supportParens = new Element(ELEMENT_OPTION);
    supportParens.setAttribute(ATTRIBUTE_NAME, valueHasParens);
    supportParens.setAttribute(ATTRIBUTE_VALUE, String.valueOf(hasParens));
    optionsElement.addContent(supportParens);
  }

  private static void writeKeywords(Set keywords, Element keywordsElement) {
    for (final Object keyword : keywords) {
      Element e = new Element(ELEMENT_KEYWORD);
      e.setAttribute(ATTRIBUTE_NAME, (String)keyword);
      keywordsElement.addContent(e);
    }
  }

  public void markDefaultSettings() {
    myDefaultSyntaxTable = mySyntaxTable;
  }

  public boolean isModified() {
    return !Comparing.equal(myDefaultSyntaxTable, getSyntaxTable());
  }


  @NonNls private static final String ELEMENT_MAPPING = "mapping";
  @NonNls private static final String ATTRIBUTE_EXT = "ext";
  @NonNls private static final String ATTRIBUTE_PATTERN = "pattern";
  @NonNls private static final String ELEMENT_REMOVED_MAPPING = "removed_mapping";
  @NonNls private static final String ATTRIBUTE_TYPE = "type";


  public static List<Pair<FileNameMatcher, String>> readAssociations(final Element e) {

    ArrayList<Pair<FileNameMatcher, String>> result = new ArrayList<Pair<FileNameMatcher, String>>();


    List mappings = e.getChildren(ELEMENT_MAPPING);

    for (Object mapping1 : mappings) {
      Element mapping = (Element)mapping1;
      String ext = mapping.getAttributeValue(ATTRIBUTE_EXT);
      String pattern = mapping.getAttributeValue(ATTRIBUTE_PATTERN);

      FileNameMatcher matcher = ext != null ? new ExtensionFileNameMatcher(ext) : FileTypeManager.parseFromString(pattern);
      result.add(new Pair<FileNameMatcher, String>(matcher, mapping.getAttributeValue(ATTRIBUTE_TYPE)));
    }

    return result;
  }

  public static List<Pair<FileNameMatcher, String>> readRemovedAssociations(final Element e) {
    ArrayList<Pair<FileNameMatcher, String>> result = new ArrayList<Pair<FileNameMatcher, String>>();
    List removedMappings = e.getChildren(ELEMENT_REMOVED_MAPPING);
    for (Object removedMapping : removedMappings) {
      Element mapping = (Element)removedMapping;
      String ext = mapping.getAttributeValue(ATTRIBUTE_EXT);
      String pattern = mapping.getAttributeValue(ATTRIBUTE_PATTERN);
      FileNameMatcher matcher = ext != null ? new ExtensionFileNameMatcher(ext) : FileTypeManager.parseFromString(pattern);
      result.add(new Pair<FileNameMatcher, String>(matcher, mapping.getAttributeValue(ATTRIBUTE_TYPE)));

    }

    return result;

  }

  public static Element writeMapping(final FileType type, final FileNameMatcher matcher, boolean specifyTypeName) {
    Element mapping = new Element(ELEMENT_MAPPING);
    if (matcher instanceof ExtensionFileNameMatcher) {
      mapping.setAttribute(ATTRIBUTE_EXT, ((ExtensionFileNameMatcher)matcher).getExtension());
    }
    else if (matcher instanceof WildcardFileNameMatcher) {
      mapping.setAttribute(ATTRIBUTE_PATTERN, ((WildcardFileNameMatcher)matcher).getPattern());
    }
    else if (matcher instanceof ExactFileNameMatcher) {
      mapping.setAttribute(ATTRIBUTE_PATTERN, ((ExactFileNameMatcher)matcher).getFileName());
    }
    else {
      return null;
    }

    if (specifyTypeName) {
      mapping.setAttribute(ATTRIBUTE_TYPE, type.getName());
    }

    return mapping;

  }

  public static Element writeRemovedMapping(final FileType type, final FileNameMatcher matcher, final boolean specifyTypeName) {
    Element mapping = new Element(ELEMENT_REMOVED_MAPPING);
    if (matcher instanceof ExtensionFileNameMatcher) {
      mapping.setAttribute(ATTRIBUTE_EXT, ((ExtensionFileNameMatcher)matcher).getExtension());
    }
    else if (matcher instanceof WildcardFileNameMatcher) {
      mapping.setAttribute(ATTRIBUTE_PATTERN, ((WildcardFileNameMatcher)matcher).getPattern());
    }
    else if (matcher instanceof ExactFileNameMatcher) {
      mapping.setAttribute(ATTRIBUTE_PATTERN, ((ExactFileNameMatcher)matcher).getFileName());
    }
    else {
      return null;
    }
    if (specifyTypeName) {
      mapping.setAttribute(ATTRIBUTE_TYPE, type.getName());
    }

    return mapping;
  }

  public SettingsEditor<AbstractFileType> getEditor() {
    return new CustomFileTypeEditor();
  }

  public void setCommenter(final Commenter commenter) {
    myCommenter = commenter;
  }

  @NotNull
  public ExternalInfo getExternalInfo() {
    return myExternalInfo;
  }
}
