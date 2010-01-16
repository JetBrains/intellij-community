package com.intellij.openapi.roots.ui.configuration;

import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleConfigurationStateImpl;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author yole
 */
public class PlatformContentEntriesConfigurable implements Configurable {
  private final Module myModule;
  private final boolean myCanMarkSources;
  private final boolean myCanMarkTestSources;
  private final JPanel myTopPanel = new JPanel(new BorderLayout());
  private ModifiableRootModel myModifiableModel;
  private CommonContentEntriesEditor myEditor;

  public PlatformContentEntriesConfigurable(final Module module, boolean canMarkSources, boolean canMarkTestSources) {
    myModule = module;
    myCanMarkSources = canMarkSources;
    myCanMarkTestSources = canMarkTestSources;
  }

  public String getDisplayName() {
    return "Project Structure";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    createEditor();
    return myTopPanel;
  }

  private void createEditor() {
    myModifiableModel = ApplicationManager.getApplication().runReadAction(new Computable<ModifiableRootModel>() {
      public ModifiableRootModel compute() {
        return ModuleRootManager.getInstance(myModule).getModifiableModel();
      }
    });

    final ModuleConfigurationStateImpl moduleConfigurationState =
      new ModuleConfigurationStateImpl(myModule.getProject(), new DefaultModulesProvider(myModule.getProject())) {
        @Override
        public ModifiableRootModel getRootModel() {
          return myModifiableModel;
        }

        @Override
        public FacetsProvider getFacetsProvider() {
          return DefaultFacetsProvider.INSTANCE;
        }
      };
    myEditor = new CommonContentEntriesEditor(myModule.getName(), moduleConfigurationState, myCanMarkSources, myCanMarkTestSources) {
      @Override
      protected List<ContentEntry> addContentEntries(VirtualFile[] files) {
        List<ContentEntry> entries = super.addContentEntries(files);
        addContentEntryPanels(entries.toArray(new ContentEntry[entries.size()]));
        return entries;
      }
    };
    JComponent component = ApplicationManager.getApplication().runReadAction(new Computable<JComponent>() {
      public JComponent compute() {
        return myEditor.createComponent();
      }
    });
    myTopPanel.add(component, BorderLayout.CENTER);
  }

  public boolean isModified() {
    return myEditor.isModified();
  }

  public void apply() throws ConfigurationException {
    myEditor.apply();
    if (myModifiableModel.isChanged()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          myModifiableModel.commit();
        }
      });
      myEditor.disposeUIResources();
      myTopPanel.remove(myEditor.getComponent());
      createEditor();
    }
  }

  public void reset() {
    myEditor.reset();
    // TODO?
  }

  public void disposeUIResources() {
    if (myEditor != null) {
      myEditor.disposeUIResources();
      myTopPanel.remove(myEditor.getComponent());
      myEditor = null;
    }
    if (myModifiableModel != null) {
      myModifiableModel.dispose();
      myModifiableModel = null;
    }
  }
}
