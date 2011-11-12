package com.intellij.find.editorHeaderActions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.EditorSearchComponent;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class VariantsCompletionAction extends AnAction {
  private Getter<JTextComponent> myTextField;
  private EditorSearchComponent myEditorSearchComponent;

  public EditorSearchComponent getEditorSearchComponent() {
    return myEditorSearchComponent;
  }

  public VariantsCompletionAction(EditorSearchComponent editorSearchComponent, Getter<JTextComponent> textField) {
    myEditorSearchComponent = editorSearchComponent;
    final AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION);
    setTextField(textField);
    if (action != null) {
      registerCustomShortcutSet(action.getShortcutSet(), getTextField());
    }
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final String prefix = getPrefix();
    if (StringUtil.isEmpty(prefix)) return;

    Editor editor = getEditorSearchComponent().getEditor();
    if (editor != null) {
      final String[] array = calcWords(prefix, editor);
      if (array.length == 0) {
        return;
      }

      FeatureUsageTracker.getInstance().triggerFeatureUsed("find.completion");
      final JList list = new JBList(array) {
        @Override
        protected void paintComponent(final Graphics g) {
          UISettings.setupAntialiasing(g);
          super.paintComponent(g);
        }
      };
      list.setBackground(EditorSearchComponent.COMPLETION_BACKGROUND_COLOR);
      list.setFont(editor.getColorsScheme().getFont(EditorFontType.PLAIN));

      Utils.showCompletionPopup(
        e.getInputEvent() instanceof MouseEvent ? getEditorSearchComponent().getToolbarComponent() : null,
        list, null, getTextField(), null);
    }
  }

  @Nullable
  private String getPrefix() {
    //Editor editor = myTextField.getEditor();
    //if (editor != null){
    //  int offset = editor.getCaretModel().getOffset();
    //  return myTextField.getText().substring(0, offset);
    //}
    int offset = getTextField().getCaretPosition();
    return getTextField().getText().substring(0, offset);
  }

  public JTextComponent getTextField() {
    return myTextField.get();
  }

  public void setTextField(Getter<JTextComponent> textField) {
    myTextField = textField;
  }

  private static String[] calcWords(final String prefix, Editor editor) {
    final Matcher matcher = NameUtil.buildMatcher(prefix, 0, true, true);
    final Set<String> words = new HashSet<String>();
    CharSequence chars = editor.getDocument().getCharsSequence();

    IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor() {
        @Override
        public void run(final CharSequence chars, @Nullable char[] charsArray, final int start, final int end) {
          final String word = chars.subSequence(start, end).toString();
          if (matcher.matches(word)) {
            words.add(word);
          }
        }
      }, chars, 0, chars.length());


    ArrayList<String> sortedWords = new ArrayList<String>(words);
    Collections.sort(sortedWords);

    return ArrayUtil.toStringArray(sortedWords);
  }
}
