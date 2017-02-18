/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.api.index;

import com.intellij.compiler.classFilesIndex.impl.MethodsUsageIndexConfigure;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class ClassFilesIndexFeaturesHolder extends AbstractProjectComponent {
  private final Map<ClassFilesIndexConfigure, ClassFilesIndexReaderBase> myEnabledIndexReaders =
    new HashMap<>();
  private final Map<ClassFilesIndexFeature, FeatureState> myEnabledFeatures = new HashMap<>();

  public static ClassFilesIndexFeaturesHolder getInstance(final Project project) {
    return project.getComponent(ClassFilesIndexFeaturesHolder.class);
  }

  protected ClassFilesIndexFeaturesHolder(final Project project) {
    super(project);
  }

  @Override
  public final void projectOpened() {
    for (final ClassFilesIndexFeature feature : ClassFilesIndexFeature.values()) {
      final RegistryValue registryValue = feature.getRegistryValue();
      registryValue.addListener(new RegistryValueListener.Adapter() {
        @Override
        public void afterValueChanged(final RegistryValue rawValue) {
          if (!rawValue.asBoolean() && myEnabledFeatures.containsKey(feature)) {
            disposeFeature(feature);
          }
        }
      }, myProject);
    }
    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    compilerManager.addBeforeTask(new CompileTask() {
      @Override
      public boolean execute(final CompileContext context) {
        close();
        return true;
      }
    });
  }

  public synchronized boolean enableFeatureIfNeed(final ClassFilesIndexFeature feature) {
    if (!feature.isEnabled()) {
      return false;
    }
    FeatureState state = myEnabledFeatures.get(feature);
    if (state == null) {
      state = initializeFeature(feature);
    }
    return state == FeatureState.AVAILABLE;
  }

  public synchronized void visitConfigures(final ConfigureVisitor visitor) {
    for (final ClassFilesIndexConfigure configure : myEnabledIndexReaders.keySet()) {
      visitor.visit(configure, true);
    }
    for (final ClassFilesIndexFeature feature : ClassFilesIndexFeature.values()) {
      if (feature.isEnabled() && !myEnabledFeatures.containsKey(feature)) {
        for (final ClassFilesIndexConfigure configure : feature.getRequiredIndicesConfigures()) {
          if (!myEnabledIndexReaders.containsKey(configure)) {
            visitor.visit(configure, false);
          }
        }
      }
    }
  }

  private synchronized void disposeFeature(final ClassFilesIndexFeature featureToRemove) {
    for (final ClassFilesIndexConfigure requiredConfigure : featureToRemove.getRequiredIndicesConfigures()) {
      boolean needClose = true;
      for (final ClassFilesIndexFeature enabledFeature : myEnabledFeatures.keySet()) {
        if (!enabledFeature.equals(featureToRemove) && enabledFeature.getRequiredIndicesConfigures().contains(requiredConfigure)) {
          needClose = false;
          break;
        }
      }
      if (needClose) {
        final ClassFilesIndexReaderBase readerToClose = myEnabledIndexReaders.remove(requiredConfigure);
        readerToClose.close();
      }
    }
    myEnabledFeatures.remove(featureToRemove);
  }

  private synchronized FeatureState initializeFeature(final ClassFilesIndexFeature feature) {
    if (myEnabledFeatures.containsKey(feature)) {
      throw new IllegalStateException(String.format("feature %s already contains", feature.getKey()));
    }
    final Map<ClassFilesIndexConfigure, ClassFilesIndexReaderBase> newIndices =
      new HashMap<>();
    FeatureState newFeatureState = FeatureState.AVAILABLE;
    for (final ClassFilesIndexConfigure requiredConfigure : feature.getRequiredIndicesConfigures()) {
      boolean isIndexAlreadyLoaded = false;
      for (final ClassFilesIndexFeature enabledFeature : myEnabledFeatures.keySet()) {
        if (enabledFeature.getRequiredIndicesConfigures().contains(requiredConfigure)) {
          isIndexAlreadyLoaded = true;
          break;
        }
      }
      if (!isIndexAlreadyLoaded) {
        final ClassFilesIndexReaderBase reader = requiredConfigure.createIndexReader(myProject);
        newIndices.put(requiredConfigure, reader);
        if (reader.isEmpty()) {
          newFeatureState = FeatureState.NOT_AVAILABLE;
        }
      }
    }
    myEnabledIndexReaders.putAll(newIndices);
    myEnabledFeatures.put(feature, newFeatureState);
    return newFeatureState;
  }

  private synchronized void close() {
    for (final ClassFilesIndexReaderBase reader : myEnabledIndexReaders.values()) {
      reader.close();
    }
    myEnabledIndexReaders.clear();
    myEnabledFeatures.clear();
  }

  @Override
  public void projectClosed() {
    close();
  }

  /**
   * try to find index with corresponding class only in currently enabled indexes
   */
  @Nullable
  @SuppressWarnings("unchecked")
  public <T extends ClassFilesIndexReaderBase> T getAvailableIndexReader(final Class<T> tClass) {
    final String indexReaderClassName = tClass.getCanonicalName();
    for (final ClassFilesIndexReaderBase reader : myEnabledIndexReaders.values()) {
      if (reader.getClass().getCanonicalName().equals(indexReaderClassName)) {
        return (T)reader;
      }
    }
    throw new RuntimeException(String.format("index reader for class %s not found", indexReaderClassName));
  }

  public Project getProject() {
    return myProject;
  }

  private enum FeatureState {
    AVAILABLE,
    NOT_AVAILABLE
  }
}