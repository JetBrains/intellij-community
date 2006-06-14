package com.intellij.codeInsight;

import com.intellij.codeInsight.editorActions.*;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.List;

/**
 *
 */
public class CodeInsightSettings implements NamedJDOMExternalizable, Cloneable, ExportableApplicationComponent {
  @NonNls private static final String EXCLUDED_PACKAGE = "EXCLUDED_PACKAGE";
  @NonNls private static final String ATTRIBUTE_NAME = "NAME";

  public static CodeInsightSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(CodeInsightSettings.class);
  }

  @NotNull
  public String getComponentName() {
    return "CodeInsightSettings";
  }

  public String getExternalFileName() {
    return "editor.codeinsight";
  }

  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  public String getPresentableName() {
    return CodeInsightBundle.message("codeinsight.settings");
  }

  public Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public boolean AUTO_POPUP_MEMBER_LOOKUP = true;
  public int MEMBER_LOOKUP_DELAY = 1000;
  public boolean AUTO_POPUP_XML_LOOKUP = true;
  public int XML_LOOKUP_DELAY = 0;
  public boolean AUTO_POPUP_PARAMETER_INFO = true;
  public int PARAMETER_INFO_DELAY = 1000;
  public boolean AUTO_POPUP_JAVADOC_INFO = false;
  public int JAVADOC_INFO_DELAY = 1000;
  public boolean AUTO_POPUP_JAVADOC_LOOKUP = true;
  public int JAVADOC_LOOKUP_DELAY = 1000;

  public int COMPLETION_CASE_SENSITIVE = FIRST_LETTER; // ALL, NONE or FIRST_LETTER
  public static final int ALL = 1;
  public static final int NONE = 2;
  public static final int FIRST_LETTER = 3;

  public boolean LIST_PACKAGES_IN_CODE = false;
  public boolean AUTOCOMPLETE_ON_CODE_COMPLETION = true;
  public boolean AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = true;
  public boolean AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION = false;
  public boolean AUTOCOMPLETE_ON_WORD_COMPLETION = false;
  public boolean AUTOCOMPLETE_COMMON_PREFIX = true;
  public boolean INSERT_SINGLE_PARENTH = false;
  public boolean INSERT_DOUBLE_PARENTH_WHEN_NO_ARGS = false;
  public boolean SHOW_STATIC_AFTER_INSTANCE = false;

  public boolean NARROW_DOWN_LOOKUP_LIST = true;
  public boolean SHOW_SIGNATURES_IN_LOOKUPS = true;
  public int LOOKUP_HEIGHT = 11;
  public boolean SORT_XML_LOOKUP_ITEMS = true;

  public boolean SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO = false;

  public boolean SMART_INDENT_ON_ENTER = true;
  public boolean INSERT_BRACE_ON_ENTER = true;
  public boolean INSERT_SCRIPTLET_END_ON_ENTER = true;
  public boolean JAVADOC_STUB_ON_ENTER = true;

  public boolean SMART_END_ACTION = true;

  public boolean AUTOINSERT_PAIR_BRACKET = true;
  public boolean AUTOINSERT_PAIR_QUOTE = true;

  public int REFORMAT_ON_PASTE = INDENT_BLOCK;
  public static final int NO_REFORMAT = 1;
  public static final int INDENT_BLOCK = 2;
  public static final int INDENT_EACH_LINE = 3;
  public static final int REFORMAT_BLOCK = 4;

  public int ADD_IMPORTS_ON_PASTE = ASK; // YES, NO or ASK
  public static final int YES = 1;
  public static final int NO = 2;
  public static final int ASK = 3;

  public boolean HIGHLIGHT_BRACES = true;
  public boolean HIGHLIGHT_SCOPE = false;

  public boolean AUTOINDENT_CLOSING_BRACE = true;
  public boolean OPTIMIZE_IMPORTS_ON_THE_FLY = false;
  public boolean ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false;

  public String[] EXCLUDED_PACKAGES = new String[0];

  public CodeInsightSettings(EditorActionManager actionManager) {
    actionManager.setActionHandler(IdeActions.ACTION_EDITOR_ENTER, new EnterHandler(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER)));
    actionManager.setActionHandler(IdeActions.ACTION_EDITOR_MOVE_LINE_END, new EndHandler(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_LINE_END)));
    actionManager.setActionHandler(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET, new SelectWordHandler(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)));
    actionManager.setActionHandler(IdeActions.ACTION_EDITOR_UNSELECT_WORD_AT_CARET, new UnSelectWordHandler(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_UNSELECT_WORD_AT_CARET)));
    actionManager.setActionHandler(IdeActions.ACTION_EDITOR_PASTE, new PasteHandler(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_PASTE)));
    actionManager.setActionHandler(IdeActions.ACTION_EDITOR_COPY, new CopyHandler(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_COPY)));
    actionManager.setActionHandler(IdeActions.ACTION_EDITOR_CUT, new CutHandler(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_CUT)));
    actionManager.setActionHandler(IdeActions.ACTION_EDITOR_JOIN_LINES, new JoinLinesHandler(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_JOIN_LINES)));
    actionManager.setActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE, new BackspaceHandler(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE)));

    TypedAction typedAction = actionManager.getTypedAction();
    typedAction.setupHandler(new TypedHandler(typedAction.getHandler()));
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    final List list = element.getChildren(EXCLUDED_PACKAGE);
    EXCLUDED_PACKAGES = new String[list.size()];
    for(int i=0; i<list.size(); i++) {
      EXCLUDED_PACKAGES [i] = ((Element) list.get(i)).getAttributeValue(ATTRIBUTE_NAME);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    for(String s: EXCLUDED_PACKAGES) {
      final Element child = new Element(EXCLUDED_PACKAGE);
      child.setAttribute(ATTRIBUTE_NAME, s);
      element.addContent(child);
    }
  }
}
