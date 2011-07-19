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
package com.intellij.framework.detection.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.PersistentHashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author nik
 */
public class DetectedFrameworksData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.framework.detection.impl.DetectedFrameworksData");
  private final Project myProject;
  private final File myFile;
  private PersistentHashMap<Integer,TIntHashSet> myFilesMap;

  public DetectedFrameworksData(Project project) {
    myProject = project;
    myFile = new File(FrameworkDetectorRegistryImpl.getDetectionDirPath() + File.separator + project.getName() + "." + project.getLocationHash() + File.separator + "files");
    try {
      myFilesMap = new PersistentHashMap<Integer, TIntHashSet>(myFile, EnumeratorIntegerDescriptor.INSTANCE, new TIntHashSetExternalizer());
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  public void saveDetected() {
    try {
      myFilesMap.close();
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  public void retainNewFiles(@NotNull Integer detectorId, @NotNull Collection<VirtualFile> files) {
    TIntHashSet set;
    try {
      set = myFilesMap.get(detectorId);
    }
    catch (IOException e) {
      LOG.info(e);
      set = new TIntHashSet();
    }
    final Iterator<VirtualFile> iterator = files.iterator();
    while (iterator.hasNext()) {
      VirtualFile file = iterator.next();
      if (file instanceof VirtualFileWithId) {
        final int fileId = ((VirtualFileWithId)file).getId();
        if (set != null && set.contains(fileId)) {
          iterator.remove();
        }
      }
    }
  }

  private static class TIntHashSetExternalizer implements DataExternalizer<TIntHashSet> {
    @Override
    public void save(DataOutput out, TIntHashSet value) throws IOException {
      out.writeInt(value.size());
      final TIntIterator iterator = value.iterator();
      while (iterator.hasNext()) {
        out.writeInt(iterator.next());
      }
    }

    @Override
    public TIntHashSet read(DataInput in) throws IOException {
      int size = in.readInt();
      final TIntHashSet set = new TIntHashSet(size);
      while (size-- > 0) {
        set.add(in.readInt());
      }
      return set;
    }
  }
}
