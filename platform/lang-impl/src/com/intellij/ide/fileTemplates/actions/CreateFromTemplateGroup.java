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

package com.intellij.ide.fileTemplates.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.actions.EditFileTemplatesAction;
import com.intellij.ide.fileTemplates.CreateFromTemplateActionReplacer;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.ui.SelectTemplateDialog;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CreateFromTemplateGroup extends ActionGroup implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.actions.CreateFromTemplateGroup");

  public void update(AnActionEvent event){
    super.update(event);
    Presentation presentation = event.getPresentation();
    FileTemplate[] allTemplates = FileTemplateManager.getInstance().getAllTemplates();
    for (FileTemplate template : allTemplates) {
      if (canCreateFromTemplate(event, template)) {
        presentation.setEnabled(true);
        return;
      }
    }
    presentation.setEnabled(false);
  }

  public AnAction[] getChildren(@Nullable AnActionEvent e){
    FileTemplateManager manager = FileTemplateManager.getInstance();
    FileTemplate[] templates = manager.getAllTemplates();

    boolean showAll = templates.length <= FileTemplateManager.RECENT_TEMPLATES_SIZE;
    if (!showAll) {
      Collection<String> recentNames = manager.getRecentNames();
      templates = new FileTemplate[recentNames.size()];
      int i = 0;
      for (String name : recentNames) {
        templates[i] = FileTemplateManager.getInstance().getTemplate(name);
        i++;
      }
    }

    Arrays.sort(templates, new Comparator<FileTemplate>() {
      public int compare(FileTemplate template1, FileTemplate template2) {
        // java first
        if (template1.isJavaClassTemplate() && !template2.isJavaClassTemplate()) {
          return -1;
        }
        if (template2.isJavaClassTemplate() && !template1.isJavaClassTemplate()) {
          return 1;
        }

        // group by type
        int i = template1.getExtension().compareTo(template2.getExtension());
        if (i != 0) {
          return i;
        }

        // group by name if same type
        return template1.getName().compareTo(template2.getName());
      }
    });
    List<AnAction> result = new ArrayList<AnAction>();

    for (FileTemplate template : templates) {
      if (canCreateFromTemplate(e, template)) {
        AnAction action = replaceAction(template);
        if (action == null) {
          action = new CreateFromTemplateAction(template);
        }
        result.add(action);
      }
    }

    if (!result.isEmpty()) {
      if (!showAll) {
        result.add(new CreateFromTemplatesAction(IdeBundle.message("action.from.file.template")));
      }

      result.add(Separator.getInstance());
      result.add(new EditFileTemplatesAction(IdeBundle.message("action.edit.file.templates")));
    }

    return result.toArray(new AnAction[result.size()]);
}

  private static AnAction replaceAction(final FileTemplate template) {
    final CreateFromTemplateActionReplacer[] actionFactories =
      ApplicationManager.getApplication().getExtensions(CreateFromTemplateActionReplacer.CREATE_FROM_TEMPLATE_REPLACER);
    for (CreateFromTemplateActionReplacer actionFactory : actionFactories) {
      AnAction action = actionFactory.replaceCreateFromFileTemplateAction(template);
      if (action != null) {
        return action;
      }
    }
    return null;
  }

  static boolean canCreateFromTemplate(AnActionEvent e, FileTemplate template){
    if (e == null) return false;
    DataContext dataContext = e.getDataContext();
    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) return false;

    PsiDirectory[] dirs = view.getDirectories();
    if (dirs.length == 0) return false;

    return FileTemplateUtil.canCreateFromTemplate(dirs, template);
  }

  private static class CreateFromTemplatesAction extends CreateFromTemplateActionBase{

    public CreateFromTemplatesAction(String title){
      super(title,null,null);
    }

    protected AnAction getReplacedAction(final FileTemplate template) {
      return replaceAction(template);
    }

    protected FileTemplate getTemplate(final Project project, final PsiDirectory dir) {
      SelectTemplateDialog dialog = new SelectTemplateDialog(project, dir);
      dialog.show();
      return dialog.getSelectedTemplate();
    }
  }

}
