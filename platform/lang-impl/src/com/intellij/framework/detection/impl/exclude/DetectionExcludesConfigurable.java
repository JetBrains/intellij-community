/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.detection.impl.exclude;

import com.intellij.framework.FrameworkType;
import com.intellij.framework.detection.impl.FrameworkDetectorRegistry;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
public class DetectionExcludesConfigurable implements Configurable {
  private final Project myProject;
  private final DetectionExcludesConfigurationImpl myConfiguration;
  private SortedListModel<ExcludeListItem> myModel;
  private JPanel myMainPanel;

  public DetectionExcludesConfigurable(@NotNull Project project, @NotNull DetectionExcludesConfigurationImpl configuration) {
    myProject = project;
    myConfiguration = configuration;
    myModel = new SortedListModel<ExcludeListItem>(ExcludeListItem.COMPARATOR);
  }

  @Nls
  @Override
  public JComponent createComponent() {
    myMainPanel = new JPanel(new BorderLayout());
    final JBList excludesList = new JBList(myModel);
    excludesList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof ExcludeListItem) {
          ((ExcludeListItem)value).renderItem(this);
        }
      }
    });
    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(excludesList)
      .disableUpAction().disableDownAction()
      .setAddAction(new Runnable() {
        @Override
        public void run() {
          doAddAction();
        }
      });
    myMainPanel.add(decorator.createPanel());
    return myMainPanel;
  }

  private void doAddAction() {
    final List<FrameworkType> types = new ArrayList<FrameworkType>();
    types.addAll(FrameworkDetectorRegistry.getInstance().getFrameworkTypes());
    Collections.sort(types, new Comparator<FrameworkType>() {
      @Override
      public int compare(FrameworkType o1, FrameworkType o2) {
        return o1.getPresentableName().compareToIgnoreCase(o2.getPresentableName());
      }
    });
    types.add(0, null);
    JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<FrameworkType>("Select Framework", types) {
      @Override
      public Icon getIconFor(FrameworkType value) {
        return value != null ? value.getIcon() : null;
      }

      @NotNull
      @Override
      public String getTextFor(FrameworkType value) {
        return value != null ? value.getPresentableName() : "All Frameworks";
      }

      @Override
      public boolean hasSubstep(FrameworkType selectedValue) {
        return selectedValue != null;
      }

      @Override
      public PopupStep onChosen(final FrameworkType frameworkType, boolean finalChoice) {
        if (frameworkType == null) {
          return doFinalStep(new Runnable() {
            @Override
            public void run() {
              chooseDirectoryAndAdd(null);
            }
          });
        }
        else {
          return addExcludedFramework(frameworkType);
        }
      }
    }).showInCenterOf(myMainPanel);
  }

  private PopupStep addExcludedFramework(final @NotNull FrameworkType frameworkType) {
    final String projectItem = "In the whole project";
    return new BaseListPopupStep<String>(null, new String[]{projectItem, "In directory..."}) {
      @Override
      public PopupStep onChosen(String selectedValue, boolean finalChoice) {
        if (selectedValue.equals(projectItem)) {
          myModel.add(new ValidExcludeListItem(frameworkType, null));
          return FINAL_CHOICE;
        }
        else {
          return doFinalStep(new Runnable() {
            @Override
            public void run() {
              chooseDirectoryAndAdd(frameworkType);
            }
          });
        }
      }
    };
  }

  private void chooseDirectoryAndAdd(final @Nullable FrameworkType type) {
    final VirtualFile[] files = FileChooser.chooseFiles(myMainPanel, FileChooserDescriptorFactory.createSingleFolderDescriptor(), myProject.getBaseDir());
    final VirtualFile file = files.length > 0 ? files[0] : null;
    if (file != null) {
      myModel.add(new ValidExcludeListItem(type, file));
    }
  }

  @Override
  public boolean isModified() {
    return !computeState().equals(myConfiguration.getState());
  }

  @Override
  public void apply() {
    myConfiguration.loadState(computeState());
  }

  private ExcludesConfigurationState computeState() {
    final ExcludesConfigurationState state = new ExcludesConfigurationState();
    for (ExcludeListItem item : myModel.getItems()) {
      final String url = item.getFileUrl();
      final String typeId = item.getFrameworkTypeId();
      if (url == null) {
        state.getFrameworkTypes().add(typeId);
      }
      else {
        state.getFiles().add(new ExcludedFileState(url, typeId));
      }
    }
    return state;
  }

  @Override
  public void reset() {
    myModel.clear();
    final ExcludesConfigurationState state = myConfiguration.getState();
    for (String typeId : state.getFrameworkTypes()) {
      final FrameworkType frameworkType = FrameworkDetectorRegistry.getInstance().findFrameworkType(typeId);
      myModel.add(frameworkType != null ? new ValidExcludeListItem(frameworkType, null) : new InvalidExcludeListItem(typeId, null));
    }
    for (ExcludedFileState fileState : state.getFiles()) {
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(fileState.getUrl());
      final String typeId = fileState.getFrameworkType();
      if (typeId == null) {
        myModel.add(file != null ? new ValidExcludeListItem(null, file) : new InvalidExcludeListItem(null, fileState.getUrl()));
      }
      else {
        final FrameworkType frameworkType = FrameworkDetectorRegistry.getInstance().findFrameworkType(typeId);
        myModel.add(frameworkType != null && file != null? new ValidExcludeListItem(frameworkType, file) : new InvalidExcludeListItem(typeId, fileState.getUrl()));
      }
    }
  }

  @Override
  public void disposeUIResources() {
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Framework Detection Excludes";
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public String getHelpTopic() {
    return null;
  }
}
