package com.intellij.ui;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Roman Chernyatchik
 *
 * It is text field with autocompletion from list of values.
 *
 * Autocompletion is implemented via LookupManager.
 * Use setVariants(..) set list of values for autocompletion.
 * For variants you can use not only instances of PresentableLookupValue, but
 * also instances of LookupValueWithPriority and LookupValueWithUIHint  
 */
public class TextFieldWithAutoCompletion extends EditorTextField {
  private List<LookupElement> myVariants;

  public TextFieldWithAutoCompletion() {
    super();
  }

  public TextFieldWithAutoCompletion(final Project project) {
    super(createDocument(project), project, PlainTextLanguage.INSTANCE.getAssociatedFileType());

    new VariantsCompletionAction();
  }

  private static Document createDocument(@Nullable final Project project) {
    if (project == null) {
      return EditorFactory.getInstance().createDocument("");
    }

    final Language language = PlainTextLanguage.INSTANCE;
    final PsiFileFactory factory = PsiFileFactory.getInstance(project);
    final FileType fileType = language.getAssociatedFileType();
    assert fileType != null;

    final long stamp = LocalTimeCounter.currentTime();
    final PsiFile psiFile = factory.createFileFromText("Dummy." + fileType.getDefaultExtension(), fileType, "", stamp, true, false);
    final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    assert document != null;
    return document;
  }

  private class VariantsCompletionAction extends AnAction {
    private VariantsCompletionAction() {
      final AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION);
      if (action != null) {
        registerCustomShortcutSet(action.getShortcutSet(), TextFieldWithAutoCompletion.this);
      }
    }

    public void actionPerformed(final AnActionEvent e) {
      final Editor editor = getEditor();
      assert editor != null;

      editor.getSelectionModel().removeSelection();
      LookupManager.getInstance(getProject()).showLookup(editor, calcLookupItems(getPrefix()), getPrefix(), null);
    }
  }

  public void setVariants(@Nullable final List<LookupElement> variants) {
    myVariants = (variants != null) ? variants : Collections.<LookupElement>emptyList();
  }

  private LookupElement[] calcLookupItems(final String prefix) {
    final List<LookupElement> items = new ArrayList<LookupElement>();
    if (prefix == null || prefix.length() == 0) {
      for (LookupElement variant : myVariants) {
        items.add(variant);
      }
    } else {
      final NameUtil.Matcher matcher = NameUtil.buildMatcher(prefix, 0, true, true);

      for (LookupElement variant : myVariants) {
        if (matcher.matches(variant.getLookupString())) {
          items.add(variant);
        }
      }
    }

    Collections.sort(items, new Comparator<LookupElement>() {
      public int compare(final LookupElement item1,
                         final LookupElement item2) {
        return item1.getLookupString().compareTo(item2.getLookupString());
      }
    });

    return items.toArray(new LookupElement[items.size()]);
  }

  private String getPrefix() {
    return getText().substring(0, getCaretModel().getOffset());
  }

}
