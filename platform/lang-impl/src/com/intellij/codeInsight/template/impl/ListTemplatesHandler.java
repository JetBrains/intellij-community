/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.CustomLiveTemplateBase;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

public class ListTemplatesHandler implements CodeInsightActionHandler {
  @Override
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull PsiFile file) {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
      return;
    }
    EditorUtil.fillVirtualSpaceUntilCaret(editor);

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    int offset = editor.getCaretModel().getOffset();
    String prefix = getPrefix(editor.getDocument(), offset, false);
    String prefixWithoutDots = getPrefix(editor.getDocument(), offset, true);

    List<TemplateImpl> matchingTemplates = new ArrayList<TemplateImpl>();
    ArrayList<TemplateImpl> applicableTemplates = SurroundWithTemplateHandler.getApplicableTemplates(editor, file, false);
    final Pattern prefixSearchPattern = Pattern.compile(".*\\b" + prefixWithoutDots + ".*");
    for (TemplateImpl template : applicableTemplates) {
      final String templateDescription = template.getDescription();
      if (template.getKey().startsWith(prefix) ||
          !prefixWithoutDots.isEmpty() && templateDescription != null && prefixSearchPattern.matcher(templateDescription).matches()) {
        matchingTemplates.add(template);
      }
    }

    MultiMap<String,CustomLiveTemplateLookupElement> customTemplatesLookupElements = listApplicableCustomTemplates(editor, file, offset);

    if (matchingTemplates.isEmpty()) {
      matchingTemplates.addAll(applicableTemplates);
      prefixWithoutDots = "";
    }

    if (matchingTemplates.isEmpty() && customTemplatesLookupElements.isEmpty()) {
      String text = prefixWithoutDots.length() == 0
                    ? CodeInsightBundle.message("templates.no.defined")
                    : CodeInsightBundle.message("templates.no.defined.with.prefix", prefix);
      HintManager.getInstance().showErrorHint(editor, text);
      return;
    }

    Collections.sort(matchingTemplates, TemplateListPanel.TEMPLATE_COMPARATOR);
    showTemplatesLookup(project, editor, file, prefixWithoutDots, matchingTemplates, customTemplatesLookupElements);
  }

  private static void showTemplatesLookup(final Project project,
                                          final Editor editor,
                                          final PsiFile file,
                                          @NotNull String prefix,
                                          @NotNull List<TemplateImpl> matchingTemplates,
                                          @NotNull MultiMap<String, CustomLiveTemplateLookupElement> customTemplatesLookupElements) {

    final LookupImpl lookup = (LookupImpl)LookupManager.getInstance(project).createLookup(editor, LookupElement.EMPTY_ARRAY, prefix,
                                                                                          new TemplatesArranger());
    for (TemplateImpl template : matchingTemplates) {
      lookup.addItem(createTemplateElement(template), new PlainPrefixMatcher(prefix));
    }

    for (Map.Entry<String, Collection<CustomLiveTemplateLookupElement>> entry : customTemplatesLookupElements.entrySet()) {
      for (CustomLiveTemplateLookupElement lookupElement : entry.getValue()) {
        lookup.addItem(lookupElement, new PlainPrefixMatcher(entry.getKey()));
      }
    }

    showLookup(lookup, file);
  }

  public static MultiMap<String, CustomLiveTemplateLookupElement> listApplicableCustomTemplates(@NotNull Editor editor, @NotNull PsiFile file, int offset) {
    final MultiMap<String, CustomLiveTemplateLookupElement> result = MultiMap.create();
    CustomTemplateCallback customTemplateCallback = new CustomTemplateCallback(editor, file, false);
    for (CustomLiveTemplate customLiveTemplate : CustomLiveTemplate.EP_NAME.getExtensions()) {
      if (customLiveTemplate instanceof CustomLiveTemplateBase && TemplateManagerImpl.isApplicable(customLiveTemplate, editor, file)) {
        String customTemplatePrefix = ((CustomLiveTemplateBase)customLiveTemplate).computeTemplateKeyWithoutContextChecking(customTemplateCallback);
        if (customTemplatePrefix != null) {
          result.putValues(customTemplatePrefix, ((CustomLiveTemplateBase)customLiveTemplate).getLookupElements(file, editor, offset));
        }
      }
    }
    return result;
  }

  private static LiveTemplateLookupElement createTemplateElement(final TemplateImpl template) {
    return new LiveTemplateLookupElementImpl(template, false) {
      @Override
      public Set<String> getAllLookupStrings() {
        String description = template.getDescription();
        if (description == null) {
          return super.getAllLookupStrings();
        }
        return ContainerUtil.newHashSet(getLookupString(), description);
      }
    };
  }

  private static String computePrefix(TemplateImpl template, String argument) {
    String key = template.getKey();
    if (argument == null) {
      return key;
    }
    if (key.length() > 0 && Character.isJavaIdentifierPart(key.charAt(key.length() - 1))) {
      return key + ' ' + argument;
    }
    return key + argument;
  }

  public static void showTemplatesLookup(final Project project, final Editor editor, Map<TemplateImpl, String> template2Argument) {
    final LookupImpl lookup = (LookupImpl)LookupManager.getInstance(project).createLookup(editor, LookupElement.EMPTY_ARRAY, "",
                                                                                          new LookupArranger.DefaultArranger());
    for (TemplateImpl template : template2Argument.keySet()) {
      String prefix = computePrefix(template, template2Argument.get(template));
      lookup.addItem(createTemplateElement(template), new PlainPrefixMatcher(prefix));
    }

    showLookup(lookup, template2Argument);
  }

  private static void showLookup(LookupImpl lookup, @Nullable Map<TemplateImpl, String> template2Argument) {
    Editor editor = lookup.getEditor();
    Project project = editor.getProject();
    lookup.addLookupListener(new MyLookupAdapter(project, editor, template2Argument));
    lookup.refreshUi(false, true);
    lookup.showLookup();
  }

  private static void showLookup(LookupImpl lookup, @NotNull PsiFile file) {
    Editor editor = lookup.getEditor();
    Project project = editor.getProject();
    lookup.addLookupListener(new MyLookupAdapter(project, editor, file));
    lookup.refreshUi(false, true);
    lookup.showLookup();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  public static String getPrefix(Document document, int offset, boolean lettersOnly) {
    CharSequence chars = document.getCharsSequence();
    int start = offset;
    while (true) {
      if (start == 0) break;
      char c = chars.charAt(start - 1);
      if (!(Character.isJavaIdentifierPart(c) || !lettersOnly && c == '.')) break;
      start--;
    }
    return chars.subSequence(start, offset).toString();
  }

  private static class MyLookupAdapter extends LookupAdapter {
    private final Project myProject;
    private final Editor myEditor;
    private final Map<TemplateImpl, String> myTemplate2Argument;
    private final PsiFile myFile;

    public MyLookupAdapter(Project project, Editor editor, @Nullable Map<TemplateImpl, String> template2Argument) {
      myProject = project;
      myEditor = editor;
      myTemplate2Argument = template2Argument;
      myFile = null;
    }

    public MyLookupAdapter(Project project, Editor editor, @Nullable PsiFile file) {
      myProject = project;
      myEditor = editor;
      myTemplate2Argument = null;
      myFile = file;
    }

    @Override
    public void itemSelected(final LookupEvent event) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.liveTemplates");
      final LookupElement item = event.getItem();
      if (item instanceof LiveTemplateLookupElementImpl) {
        final TemplateImpl template = ((LiveTemplateLookupElementImpl)item).getTemplate();
        final String argument = myTemplate2Argument != null ? myTemplate2Argument.get(template) : null;
        new WriteCommandAction(myProject) {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            ((TemplateManagerImpl)TemplateManager.getInstance(myProject)).startTemplateWithPrefix(myEditor, template, null, argument);
          }
        }.execute();
      }
      else if (item instanceof CustomLiveTemplateLookupElement) {
        if (myFile != null) {
          new WriteCommandAction(myProject) {
            @Override
            protected void run(@NotNull Result result) throws Throwable {
              ((CustomLiveTemplateLookupElement)item).expandTemplate(myEditor, myFile);
            }
          }.execute();
        }
      }
    }
  }

  private static class TemplatesArranger extends LookupArranger {

    @Override
    public Pair<List<LookupElement>, Integer> arrangeItems(@NotNull Lookup lookup, boolean onExplicitAction) {
      LinkedHashSet<LookupElement> result = new LinkedHashSet<LookupElement>();
      List<LookupElement> items = getMatchingItems();
      for (LookupElement item : items) {
        if (item.getLookupString().startsWith(lookup.itemPattern(item))) {
          result.add(item);
        }
      }
      result.addAll(items);
      ArrayList<LookupElement> list = new ArrayList<LookupElement>(result);
      int selected = lookup.isSelectionTouched() ? list.indexOf(lookup.getCurrentItem()) : 0;
      return new Pair<List<LookupElement>, Integer>(list, selected >= 0 ? selected : 0);
    }

    @Override
    public LookupArranger createEmptyCopy() {
      return new TemplatesArranger();
    }
  }
}
