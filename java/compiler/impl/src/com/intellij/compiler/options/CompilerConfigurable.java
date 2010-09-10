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
package com.intellij.compiler.options;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerSettingsFactory;
import com.intellij.compiler.impl.rmiCompiler.RmicConfiguration;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfigurable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FileStatusManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CompilerConfigurable implements SearchableConfigurable.Parent {
  private final Project myProject;
  private static final Icon ICON = IconLoader.getIcon("/general/configurableCompiler.png");
  private final CompilerUIConfigurable myCompilerUIConfigurable;
  private Configurable[] myKids;

  public CompilerConfigurable(Project project) {
    myProject = project;
    myCompilerUIConfigurable = new CompilerUIConfigurable(myProject);
  }

  public String getDisplayName() {
    return CompilerBundle.message("compiler.configurable.display.name");
  }

  public Icon getIcon() {
    return ICON;
  }

  public String getHelpTopic() {
    return "project.propCompiler";
  }

  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  public JComponent createComponent() {
    return myCompilerUIConfigurable.createComponent();
  }

  public boolean hasOwnContent() {
    return true;
  }

  public boolean isVisible() {
    return true;
  }

  public boolean isModified() {
    return myCompilerUIConfigurable.isModified();
  }

  public void apply() throws ConfigurationException {
    myCompilerUIConfigurable.apply();
  }

  public void reset() {
    myCompilerUIConfigurable.reset();
  }

  public void disposeUIResources() {
    myCompilerUIConfigurable.disposeUIResources();
  }

  public Configurable[] getConfigurables() {
    if (myKids == null) {
      List<Configurable> kids = new ArrayList<Configurable>();

      CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
      final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, true);

      final ExcludedEntriesConfigurable excludes = new ExcludedEntriesConfigurable(myProject, descriptor, compilerConfiguration.getExcludedEntriesConfiguration()) {
        public void apply() {
          super.apply();
          FileStatusManager.getInstance(myProject).fileStatusesChanged(); // refresh exclude from compile status
          //ProjectView.getInstance(myProject).refresh();
        }
      };

      kids.add(createExcludesWrapper(excludes));

      ArrayList<Configurable> additional = new ArrayList<Configurable>();

      final CompilerSettingsFactory[] factories = Extensions.getExtensions(CompilerSettingsFactory.EP_NAME, myProject);
      if (factories.length > 0) {
        for (CompilerSettingsFactory factory : factories) {
          additional.add(factory.create(myProject));
        }
        Collections.sort(additional, new Comparator<Configurable>() {
          public int compare(final Configurable o1, final Configurable o2) {
            return Comparing.compare(o1.getDisplayName(), o2.getDisplayName());
          }
        });
      }

      additional.add(0, new RmicConfigurable(RmicConfiguration.getSettings(myProject)));
      additional.add(0, new AnnotationProcessorsConfigurable(myProject));
      additional.add(0, new JavaCompilersTab(myProject, compilerConfiguration.getRegisteredJavaCompilers(), compilerConfiguration.getDefaultCompiler()));

      kids.addAll(additional);
      myKids = kids.toArray(new Configurable[kids.size()]);
    }

    return myKids;

  }

  private static Configurable createExcludesWrapper(final ExcludedEntriesConfigurable excludes) {
    return new SearchableConfigurable(){
        @Nls
        public String getDisplayName() {
          return "Excludes";
        }

        public Icon getIcon() {
          return null;
        }

        public String getHelpTopic() {
          return "reference.projectsettings.compiler.excludes";
        }

        public JComponent createComponent() {
          return excludes.createComponent();
        }

        public void apply() {
          excludes.apply();
        }

        public boolean isModified() {
          return excludes.isModified();
        }

        public void reset() {
          excludes.reset();
        }

        public void disposeUIResources() {
          excludes.disposeUIResources();
        }

      public String getId() {
        return getHelpTopic();
      }

      public Runnable enableSearch(String option) {
        return null;
      }
    };
  }
}
