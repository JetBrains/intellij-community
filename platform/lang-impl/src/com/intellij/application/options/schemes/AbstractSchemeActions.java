/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.application.options.schemes;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class AbstractSchemeActions<T extends Scheme> {
  
  private final Collection<String> mySchemeImportersNames;
  private final Collection<String> mySchemeExporterNames;
  private AbstractSchemesPanel<T> mySchemesPanel;
  
  protected AbstractSchemeActions(@NotNull AbstractSchemesPanel<T> schemesPanel) {
    mySchemesPanel = schemesPanel;
    mySchemeImportersNames = getSchemeImportersNames();
    mySchemeExporterNames = getSchemeExporterNames();
  }
  
    
  protected Collection<String> getSchemeImportersNames() {
    List<String> importersNames = new ArrayList<>();
    for (SchemeImporterEP importerEP : SchemeImporterEP.getExtensions(getSchemeType())) {
      importersNames.add(importerEP.name);
    }
    return importersNames;
  }
  
  private Collection<String> getSchemeExporterNames() {
    List<String> exporterNames = new ArrayList<>();
    for (SchemeExporterEP exporterEP : SchemeExporterEP.getExtensions(getSchemeType())) {
      exporterNames.add(exporterEP.name);
    }
    return exporterNames;
  }

  public final Collection<AnAction> getActions() {
    List<AnAction> actions = new ArrayList<>();
    actions.add(new CopyAction());
    actions.add(new RenameAction());
    actions.add(new ResetAction());
    actions.add(new DeleteAction());
    if (!mySchemeExporterNames.isEmpty()) {
      actions.add(new ActionGroupPopupAction(ApplicationBundle.message("settings.editor.scheme.export"), mySchemeExporterNames) {
        @NotNull
        @Override
        protected AnAction createAction(@NotNull String actionName) {
          return new ExportAction(actionName);
        }
      });
    }
    actions.add(new Separator());
    if (!mySchemeImportersNames.isEmpty()) {
      actions.add(new ActionGroupPopupAction(ApplicationBundle.message("settings.editor.scheme.import"), mySchemeImportersNames) {
        @NotNull
        @Override
        protected AnAction createAction(@NotNull String actionName) {
          return new ImportAction(actionName);
        }
      });
    }
    addAdditionalActions(actions);
    return actions;
  }
  
  protected void addAdditionalActions(@NotNull List<AnAction> defaultActions) {}
  
  private class ResetAction extends DumbAwareAction {
    
    public ResetAction() {
      super(ApplicationBundle.message("settings.editor.scheme.reset"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null) {
        mySchemesPanel.cancelEdit();
        resetScheme(currentScheme);
      }                                                                             
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      T scheme = getCurrentScheme();
      if(scheme != null && mySchemesPanel.getModel().canResetScheme(scheme)) {
        p.setVisible(true);
        p.setEnabled(mySchemesPanel.getModel().differsFromDefault(scheme));
      }
      else {
        p.setEnabledAndVisible(false);
      }
    }
  }
  
  
  private class CopyAction extends DumbAwareAction {
    public CopyAction() {
      super(ApplicationBundle.message("settings.editor.scheme.copy"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null) {
        mySchemesPanel.cancelEdit();
        duplicateScheme(currentScheme,
                        SchemeNameGenerator.getUniqueName(
                      SchemeManager.getDisplayName(currentScheme), 
                      name -> mySchemesPanel.getModel().nameExists(name)));
        currentScheme = getCurrentScheme();
        if (currentScheme != null)  {
          mySchemesPanel.startEdit();
        }
      }
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      T scheme = getCurrentScheme();
      p.setEnabledAndVisible(scheme != null && mySchemesPanel.getModel().canDuplicateScheme(scheme));
    }
  }
  
  
  private class RenameAction extends DumbAwareAction {
    public RenameAction() {
      super("Rename...");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null) {
        mySchemesPanel.startEdit();
      }
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      T scheme = getCurrentScheme();
      p.setEnabledAndVisible(scheme != null && mySchemesPanel.getModel().canRenameScheme(scheme));
    }
  }
  
  private class DeleteAction extends DumbAwareAction {
    public DeleteAction() {
      super(ApplicationBundle.message("settings.editor.scheme.delete"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null) {
        mySchemesPanel.cancelEdit();
        deleteScheme(currentScheme);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      T scheme = getCurrentScheme(); 
      p.setEnabledAndVisible(scheme != null && mySchemesPanel.getModel().canDeleteScheme(scheme));
    }
  }

  private abstract class ActionGroupPopupAction extends DumbAwareAction {
    private final Collection<String> myActionNames;

    public ActionGroupPopupAction(@NotNull String groupName, @NotNull Collection<String> actionNames) {
      super(groupName);
      myActionNames = actionNames;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ListPopup listPopup =JBPopupFactory.getInstance().createActionGroupPopup(getTemplatePresentation().getText(), new ActionGroup() {
        @NotNull
        @Override
        public AnAction[] getChildren(@Nullable AnActionEvent e) {
          List<AnAction> namedActions = new ArrayList<>();
          for (String actionName : myActionNames) {
            namedActions.add(createAction(actionName));
          }
          return namedActions.toArray(new AnAction[namedActions.size()]);
        }
      }, e.getDataContext(), null, true);
      listPopup.showUnderneathOf(mySchemesPanel.getToolbar());
    }

    @NotNull
    protected abstract AnAction createAction(@NotNull String actionName);
  }
  
  private class ImportAction extends DumbAwareAction {

    private String myImporterName;

    public ImportAction(@NotNull String importerName) {
      super(importerName);
      myImporterName = importerName;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      mySchemesPanel.cancelEdit();
      importScheme(myImporterName);
    }
  }
  
  private class ExportAction extends DumbAwareAction {
    private String myExporterName;

    public ExportAction(@NotNull String exporterName) {
      super(exporterName);
      myExporterName = exporterName;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null) {
        mySchemesPanel.cancelEdit();
        exportScheme(currentScheme, myExporterName);
      }
    }
  }
  
  protected abstract void importScheme(@NotNull String importerName);

  protected abstract void resetScheme(@NotNull T scheme);
  
  protected abstract void duplicateScheme(@NotNull T scheme, @NotNull String newName);
  
  protected abstract void deleteScheme(@NotNull T scheme);
  
  protected abstract void exportScheme(@NotNull T scheme, @NotNull String exporterName);

  protected abstract void onSchemeChanged(@Nullable T scheme);
  
  protected abstract void renameScheme(@NotNull T scheme, @NotNull String newName);
  
  @Nullable
  protected final T getCurrentScheme() {
    return mySchemesPanel.getSelectedScheme();
  }
  
  protected abstract Class<T> getSchemeType();

  public AbstractSchemesPanel<T> getSchemesPanel() {
    return mySchemesPanel;
  }
}

