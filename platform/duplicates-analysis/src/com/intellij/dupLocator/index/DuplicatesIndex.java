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
package com.intellij.dupLocator.index;

import com.intellij.dupLocator.DuplicatesProfile;
import com.intellij.dupLocator.DuplocateVisitor;
import com.intellij.dupLocator.DuplocatorState;
import com.intellij.dupLocator.LightDuplicateProfile;
import com.intellij.dupLocator.treeHash.FragmentsCollector;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.lang.Language;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.util.SystemProperties;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Maxim.Mossienko on 12/11/13.
 */
public class DuplicatesIndex extends FileBasedIndexExtension<Integer, IntArrayList> {
  static boolean ourEnabled = SystemProperties.getBooleanProperty("idea.enable.duplicates.online.calculation",
                                                                  true);
  static final boolean ourEnabledLightProfiles = true;
  private static boolean ourEnabledOldProfiles = false;

  @NonNls public static final ID<Integer, IntArrayList> NAME = ID.create("DuplicatesIndex");
  private static final int myBaseVersion = 25;

  private final FileBasedIndex.InputFilter myInputFilter = file -> {
    if (!ourEnabled ||
        !file.isInLocalFileSystem()  // skip library sources
       ) {
      return false;
    }
    DuplicatesProfile duplicatesProfile = findDuplicatesProfile(file.getFileType());
    if (duplicatesProfile instanceof LightDuplicateProfile) {
      return ((LightDuplicateProfile)duplicatesProfile).acceptsFile(file);
    }
    return duplicatesProfile != null;
  };

  private final DataExternalizer<IntArrayList> myValueExternalizer = new DataExternalizer<IntArrayList>() {
    @Override
    public void save(@NotNull DataOutput out, IntArrayList list) throws IOException {
      if (list.size() == 2) {
        DataInputOutputUtil.writeINT(out, list.getInt(0));
        DataInputOutputUtil.writeINT(out, list.getInt(1));
      }
      else {
        DataInputOutputUtil.writeINT(out, -list.size());
        int prev = 0;
        for (int i = 0, len = list.size(); i < len; i+=2) {
          int value = list.getInt(i);
          DataInputOutputUtil.writeINT(out, value - prev);
          prev = value;
          DataInputOutputUtil.writeINT(out, list.getInt(i + 1));
        }
      }
    }

    @Override
    public IntArrayList read(@NotNull DataInput in) throws IOException {
      int capacityOrValue = DataInputOutputUtil.readINT(in);
      if (capacityOrValue >= 0) {
        IntArrayList list = new IntArrayList(2);
        list.add(capacityOrValue);
        list.add(DataInputOutputUtil.readINT(in));
        return list;
      }
      capacityOrValue = -capacityOrValue;
      IntArrayList list = new IntArrayList(capacityOrValue);
      int prev = 0;
      while(capacityOrValue > 0) {
        int value = DataInputOutputUtil.readINT(in) + prev;
        list.add(value);
        prev = value;
        list.add(DataInputOutputUtil.readINT(in));
        capacityOrValue -= 2;
      }
      return list;
    }
  };

  private final DataIndexer<Integer, IntArrayList, FileContent> myIndexer = new DataIndexer<Integer, IntArrayList, FileContent>() {
    @Override
    @NotNull
    public Int2ObjectMap<IntArrayList> map(@NotNull final FileContent inputData) {
      FileType type = inputData.getFileType();

      DuplicatesProfile profile = findDuplicatesProfile(type);
      if (profile == null || !profile.acceptsContentForIndexing(inputData)) {
        return Int2ObjectMaps.emptyMap();
      }

      try {
        PsiDependentFileContent fileContent = (PsiDependentFileContent)inputData;

        if (profile instanceof LightDuplicateProfile && ourEnabledLightProfiles) {
          final Int2ObjectOpenHashMap<IntArrayList> result = new Int2ObjectOpenHashMap<>();
          LighterAST ast = fileContent.getLighterAST();

          ((LightDuplicateProfile)profile).process(ast, new LightDuplicateProfile.Callback() {
            @Override
            public void process(int hash, int hash2, @NotNull LighterAST ast, LighterASTNode @NotNull ... nodes) {
              IntArrayList list = result.get(hash);
              if (list == null) {
                result.put(hash, list = new IntArrayList(2));
              }
              list.add(nodes[0].getStartOffset());
              list.add(hash2);
            }
          });
          return result;
        }
        MyFragmentsCollector collector = new MyFragmentsCollector(profile, ((LanguageFileType)type).getLanguage());
        DuplocateVisitor visitor = profile.createVisitor(collector, true);

        visitor.visitNode(fileContent.getPsiFile());

        return collector.getMap();
      }
      catch (StackOverflowError ae) {
        return Int2ObjectMaps.emptyMap(); // todo Maksim
      }
    }
  };

  @Nullable
  public static DuplicatesProfile findDuplicatesProfile(FileType fileType) {
    if (!(fileType instanceof LanguageFileType)) return null;
    Language language = ((LanguageFileType)fileType).getLanguage();
    DuplicatesProfile profile = DuplicatesProfile.findProfileForLanguage(language);
    return profile != null &&
           (ourEnabledOldProfiles && profile.supportDuplicatesIndex() ||
            profile instanceof LightDuplicateProfile) ? profile : null;
  }

  @Override
  public int getVersion() {
    return myBaseVersion + (ourEnabled ? 0xFF : 0) + (ourEnabledLightProfiles ? 0x80 : 0) + (ourEnabledOldProfiles ? 0x21 : 0);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @NotNull
  @Override
  public ID<Integer, IntArrayList> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<Integer, IntArrayList, FileContent> getIndexer() {
    return myIndexer;
  }

  @NotNull
  @Override
  public DataExternalizer<IntArrayList> getValueExternalizer() {
    return myValueExternalizer;
  }

  @NotNull
  @Override
  public KeyDescriptor<Integer> getKeyDescriptor() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  //private static final TracingData myTracingData = new TracingData();
  private static final TracingData myTracingData = null;

  private static final class MyFragmentsCollector implements FragmentsCollector {
    private final Int2ObjectOpenHashMap<IntArrayList> myMap = new Int2ObjectOpenHashMap<>();
    private final DuplicatesProfile myProfile;
    private final DuplocatorState myDuplocatorState;

    MyFragmentsCollector(DuplicatesProfile profile, Language language) {
      myProfile = profile;
      myDuplocatorState = profile.getDuplocatorState(language);
    }

    @Override
    public void add(int hash, int cost, @Nullable PsiFragment frag) {
      if (!isIndexedFragment(frag, cost, myProfile, myDuplocatorState)) {
        return;
      }

      if (myTracingData != null) myTracingData.record(hash, cost, frag);

      IntArrayList list = myMap.get(hash);
      if (list == null) { myMap.put(hash, list = new IntArrayList()); }
      list.add(frag.getStartOffset());
      list.add(0);
    }

    public Int2ObjectOpenHashMap<IntArrayList> getMap() {
      return myMap;
    }
  }

  static boolean isIndexedFragment(@Nullable PsiFragment frag, int cost, DuplicatesProfile profile, DuplocatorState duplocatorState) {
    if(frag == null) return false;
    return profile.shouldPutInIndex(frag, cost, duplocatorState);
  }

  @TestOnly
  public static boolean setEnabled(boolean value) {
    boolean old = ourEnabled;
    ourEnabled = value;
    return old;
  }

  @TestOnly
  public static boolean setEnabledOldProfiles(boolean value) {
    boolean old = ourEnabledOldProfiles;
    ourEnabledOldProfiles = value;
    return old;
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }
}
