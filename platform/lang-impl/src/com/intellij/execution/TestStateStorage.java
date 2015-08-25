/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * @author Dmitry Avdeev
 */
public class TestStateStorage extends AbstractProjectComponent{

  private static final File TEST_HISTORY_PATH = new File(PathManager.getSystemPath(), "testHistory");
  public static File getTestHistoryRoot(Project project) {
    return new File(TEST_HISTORY_PATH, project.getLocationHash());
  }

  public static class Record {
    public final int magnitude;
    public final Date date;

    public Record(int magnitude, Date date) {
      this.magnitude = magnitude;
      this.date = date;
    }
  }

  private static final Logger LOG = Logger.getInstance(TestStateStorage.class);
  private PersistentHashMap<String, Record> myMap;

  public static TestStateStorage getInstance(@NotNull Project project) {
    return project.getComponent(TestStateStorage.class);
  }

  public TestStateStorage(Project project) {
    super(project);

    File file = new File(getTestHistoryRoot(project).getPath() + "/testStateMap");
    FileUtilRt.createParentDirs(file);
    try {
      myMap = new PersistentHashMap<String, Record>(file, new EnumeratorStringDescriptor(), new DataExternalizer<Record>() {
        @Override
        public void save(@NotNull DataOutput out, Record value) throws IOException {
          out.writeInt(value.magnitude);
          out.writeLong(value.date.getTime());
        }

        @Override
        public Record read(@NotNull DataInput in) throws IOException {
          return new Record(in.readInt(), new Date(in.readLong()));
        }
      });
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Nullable
  public Record getState(String testUrl) {
    try {
      return myMap == null ? null : myMap.get(testUrl);
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  public void writeState(@NotNull String testUrl, Record record) {
    if (myMap == null) return;
    try {
      myMap.put(testUrl, record);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  public void disposeComponent() {
    try {
      myMap.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }
}
