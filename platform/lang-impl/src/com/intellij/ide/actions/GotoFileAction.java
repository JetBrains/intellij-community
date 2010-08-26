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

package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * "Go to | File" action implementation.
 *
 * @author Eugene Belyaev
 * @author Constantine.Plotnikov
 */
public class GotoFileAction extends GotoActionBase implements DumbAware {

  @Override
  public void gotoActionPerformed(AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.file");
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final GotoFileModel gotoFileModel = new GotoFileModel(project);
    final ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, gotoFileModel, getPsiContext(e));
    final FilterUI filterUI = new FilterUI(popup, gotoFileModel, project);
    popup.invoke(new ChooseByNamePopupComponent.Callback() {
      public void onClose() {
        if (GotoFileAction.class.equals(myInAction)) {
          myInAction = null;
        }
        filterUI.close();
      }

      public void elementChosen(Object element) {
        final PsiFile file = (PsiFile)element;
        if (file == null) return;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final OpenFileDescriptor descriptor =
                new OpenFileDescriptor(project, file.getVirtualFile(), popup.getLinePosition(), popup.getColumnPosition());
            if (descriptor.canNavigate()) {
              descriptor.navigate(true);
            }
          }
        }, ModalityState.NON_MODAL);
      }
    }, ModalityState.current(), true);
  }

  /**
   * This class contains UI related to filtering functionality.
   */
  private static class FilterUI {
    /**
     * an icon to use
     */
    private static final Icon FILTER_ICON = IconLoader.getIcon("/icons/inspector/useFilter.png");
    /**
     * a parent popup
     */
    final ChooseByNamePopup myParentPopup;
    /**
     * action toolbar
     */
    final ActionToolbar myToolbar;
    /**
     * a file type chooser, only one instance is used
     */
    final ElementsChooser<FileType> myChooser;
    /**
     * A panel that contains chooser
     */
    final JPanel myChooserPanel;
    /**
     * a file type popup, the value is non-null if popup is active
     */
    JBPopup myPopup;
    /**
     * a project to use. The project is used for dimension service.
     */
    final Project myProject;

    /**
     * A constuctor
     *
     * @param popup         a parent popup
     * @param gotoFileModel a model for popup
     * @param project       a context project
     */
    FilterUI(final ChooseByNamePopup popup, final GotoFileModel gotoFileModel, final Project project) {
      myParentPopup = popup;
      DefaultActionGroup actionGroup = new DefaultActionGroup("go.to.file.filter", false);
      ToggleAction action = new ToggleAction("Filter", "Filter files by type", FILTER_ICON) {
        public boolean isSelected(final AnActionEvent e) {
          return myPopup != null;
        }

        public void setSelected(final AnActionEvent e, final boolean state) {
          if (state) {
            createPopup();
          }
          else {
            close();
          }
        }
      };
      actionGroup.add(action);
      myToolbar = ActionManager.getInstance().createActionToolbar("gotfile.filter", actionGroup, true);
      myToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
      myToolbar.updateActionsImmediately();
      myToolbar.getComponent().setFocusable(false);
      myToolbar.getComponent().setBorder(null);
      myProject = project;
      myChooser = createFileTypeChooser(gotoFileModel);
      myChooserPanel = createChooserPanel();
      popup.setToolArea(myToolbar.getComponent());
    }

    /**
     * @return a panel with chooser and buttons
     */
    private JPanel createChooserPanel() {
      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      panel.add(myChooser);
      JPanel buttons = new JPanel();
      JButton all = new JButton("All");
      all.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          myChooser.setAllElementsMarked(true);
        }
      });
      buttons.add(all);
      JButton none = new JButton("None");
      none.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          myChooser.setAllElementsMarked(false);
        }
      });
      buttons.add(none);
      JButton invert = new JButton("Invert");
      invert.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final int count = myChooser.getElementCount();
          for (int i = 0; i < count; i++) {
            FileType type = myChooser.getElementAt(i);
            myChooser.setElementMarked(type, !myChooser.isElementMarked(type));
          }
        }
      });
      buttons.add(invert);
      panel.add(buttons);
      return panel;
    }

    /**
     * Create a file type chooser
     *
     * @param gotoFileModel a model to update
     * @return a created file chooser
     */
    private ElementsChooser<FileType> createFileTypeChooser(final GotoFileModel gotoFileModel) {
      List<FileType> elements = new ArrayList<FileType>();
      ContainerUtil.addAll(elements, FileTypeManager.getInstance().getRegisteredFileTypes());
      Collections.sort(elements, FileTypeComparator.INSTANCE);
      final ElementsChooser<FileType> chooser = new ElementsChooser<FileType>(elements, true) {
        @Override
        protected String getItemText(@NotNull final FileType value) {
          return value.getName();
        }

        @Override
        protected Icon getItemIcon(final FileType value) {
          return value.getIcon();
        }
      };
      chooser.setFocusable(false);
      final GotoFileConfiguration config = GotoFileConfiguration.getInstance(myProject);
      final int count = chooser.getElementCount();
      for (int i = 0; i < count; i++) {
        FileType type = chooser.getElementAt(i);
        if (!DumbService.getInstance(myProject).isDumb() && !config.isFileTypeVisible(type)) {
          chooser.setElementMarked(type, false);
        }
      }
      updateModel(gotoFileModel, chooser);
      chooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<FileType>() {
        public void elementMarkChanged(final FileType element, final boolean isMarked) {
          config.setFileTypeVisible(element, isMarked);
          updateModel(gotoFileModel, chooser);
        }
      });
      return chooser;
    }

    /**
     * Update model basing on the chooser state
     *
     * @param gotoFileModel a model
     * @param chooser       a file type chooser
     */
    private void updateModel(final GotoFileModel gotoFileModel, ElementsChooser<FileType> chooser) {
      final List<FileType> markedElements = chooser.getMarkedElements();
      gotoFileModel.setFileTypes(markedElements.toArray(new FileType[markedElements.size()]));
      myParentPopup.rebuildList();
    }


    /**
     * Create and show popup
     */
    private void createPopup() {
      if (myPopup != null) {
        return;
      }
      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(myChooserPanel, myChooser).setModalContext(false).setFocusable(false)
          .setResizable(true).setCancelOnClickOutside(false).setMinSize(new Dimension(200, 200))
          .setDimensionServiceKey(myProject, "GotoFile_FileTypePopup", false).createPopup();
      myPopup.addListener(new JBPopupListener.Adapter() {
        public void onClosed(LightweightWindowEvent event) {
          myPopup = null;
        }
      });
      myPopup.showUnderneathOf(myToolbar.getComponent());
    }

    /**
     * close the file type filter
     */
    public void close() {
      if (myPopup != null) {
        myPopup.dispose();
      }
    }

    /**
     * A file type comparator. The comparison rules are applied in the following order.
     * <ol>
     * <li>Unknown file type is greatest.</li>
     * <li>Text files are less then binary ones.</li>
     * <li>File type with greater name is greater (case is ignored).</li>
     * </ol>
     */
    static class FileTypeComparator implements Comparator<FileType> {
      /**
       * an instance of comparator
       */
      static final Comparator<FileType> INSTANCE = new FileTypeComparator();

      /**
       * {@inheritDoc}
       */
      public int compare(final FileType o1, final FileType o2) {
        if (o1 == o2) {
          return 0;
        }
        if (o1 == FileTypes.UNKNOWN) {
          return 1;
        }
        if (o2 == FileTypes.UNKNOWN) {
          return -1;
        }
        if (o1.isBinary() && !o2.isBinary()) {
          return 1;
        }
        if (!o1.isBinary() && o2.isBinary()) {
          return -1;
        }
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    }
  }
}
