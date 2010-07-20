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

package com.intellij.ui;

import com.intellij.codeInsight.lookup.LookupArranger;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.Function;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.ContainerUtil;
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
  private String myAdText;

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
      showLookup();
    }
  }

  public void showLookup() {
    if (LookupManager.getInstance(getProject()).getActiveLookup() != null) return;
    final Editor editor = getEditor();
    assert editor != null;

    editor.getSelectionModel().removeSelection();
    final String lookupPrefix = getCurrentLookupPrefix(getCurrentTextPrefix());
    final LookupImpl lookup =
      (LookupImpl)LookupManager.getInstance(getProject()).createLookup(editor,
                                                                       calcLookupItems(lookupPrefix),
                                                                       lookupPrefix != null ? lookupPrefix : "",
                                                                       LookupArranger.DEFAULT);
    final String advertisementText = getAdvertisementText();
    if (!StringUtil.isEmpty(advertisementText)) {
      lookup.setAdvertisementText(advertisementText);
      lookup.refreshUi();
    }
    lookup.show();
  }

  public void setAdvertisementText(@Nullable String text) {
    myAdText = text;
  }

  public String getAdvertisementText() {
    return myAdText;
  }

  public void setVariants(@Nullable final List<LookupElement> variants) {
    myVariants = (variants != null) ? variants : Collections.<LookupElement>emptyList();
  }

  public void setVariants(@Nullable final String[] variants) {
    myVariants = (variants == null)
       ? Collections.<LookupElement>emptyList()
       : ContainerUtil.map(variants, new Function<String, LookupElement>() {
      public LookupElement fun(final String s) {
        return LookupElementBuilder.create(s);
      }
    });
  }

  private LookupElement[] calcLookupItems(@Nullable final String lookupPrefix) {
    if (lookupPrefix == null) {
      return new LookupElement[0];
    }

    final List<LookupElement> items = new ArrayList<LookupElement>();
    if (lookupPrefix.length() == 0) {
      items.addAll(myVariants);
    } else {
      final NameUtil.Matcher matcher = NameUtil.buildMatcher(lookupPrefix, 0, true, true);

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

  /**
   * Returns prefix for autocompletion and lookup items matching.
   */
  @Nullable
  protected String getCurrentLookupPrefix(final String currentTextPrefix) {
    return currentTextPrefix;
  }

  private String getCurrentTextPrefix() {
    return getText().substring(0, getCaretModel().getOffset());
  }

}
