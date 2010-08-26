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

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.impl.autodetecting.model.DetectedFacetInfo;
import com.intellij.facet.pointers.FacetPointer;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.BidirectionalMultiMap;
import com.intellij.util.fileIndex.AbstractFileIndex;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class FacetDetectionIndex extends AbstractFileIndex<FacetDetectionIndexEntry> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.autodetecting.FacetDetectionIndex");
  private static final byte CURRENT_VERSION = 1;
  @NonNls private static final String CACHE_DIRECTORY_NAME = "facets";
  private final FileTypeManager myFileTypeManager;
  private final Set<FileType> myFileTypes;
  private final Set<FacetType> myNewFacetTypes = new THashSet<FacetType>();
  private final FacetPointersManager myFacetPointersManager;
  private final FacetAutodetectingManagerImpl myAutodetectingManager;
  private final BidirectionalMultiMap<String, FacetPointer> myFacets;
  private final BidirectionalMultiMap<String, Integer> myDetectedFacetIds;

  public FacetDetectionIndex(final Project project, final FacetAutodetectingManagerImpl autodetectingManager, Set<FileType> fileTypes) {
    super(project);
    myAutodetectingManager = autodetectingManager;
    myFileTypes = new THashSet<FileType>(fileTypes);
    myFileTypeManager = FileTypeManager.getInstance();
    myFacetPointersManager = FacetPointersManager.getInstance(project);
    myFacets = new BidirectionalMultiMap<String, FacetPointer>();
    myDetectedFacetIds = new BidirectionalMultiMap<String, Integer>();
  }

  protected FacetDetectionIndexEntry createIndexEntry(final DataInputStream input) throws IOException {
    return new FacetDetectionIndexEntry(input, myFacetPointersManager);
  }

  public boolean belongs(final VirtualFile file) {
    FileType fileType = myFileTypeManager.getFileTypeByFile(file);
    return myFileTypes.contains(fileType);
  }

  protected String getLoadingIndicesMessage() {
    return ProjectBundle.message("progress.text.loading.facet.detection.indices");
  }

  public byte getCurrentVersion() {
    return CURRENT_VERSION;
  }

  public String getCachesDirName() {
    return CACHE_DIRECTORY_NAME;
  }

  public static File getDetectedFacetsFile(@NotNull Project project) {
    return new File(PathManager.getSystemPath() + File.separator + CACHE_DIRECTORY_NAME + File.separator + project.getName() + ".detected." + project.getLocationHash());
  }

  protected void readHeader(final DataInputStream input) throws IOException {
    int size = input.readInt();
    Set<String> facetTypesInCache = new THashSet<String>();
    while (size-- > 0) {
      facetTypesInCache.add(input.readUTF());
    }
    Set<String> unknownTypes = new THashSet<String>(facetTypesInCache);
    for (FacetType type : FacetTypeRegistry.getInstance().getFacetTypes()) {
      unknownTypes.remove(type.getStringId());
      if (!facetTypesInCache.contains(type.getStringId())) {
        myNewFacetTypes.add(type);
      }
    }
    if (!unknownTypes.isEmpty()) {
      LOG.info("Unknown facet types in cache: " + new HashSet<String>(unknownTypes));
    }
  }

  @Nullable
  protected Set<FileType> getFileTypesToRefresh() {
    if (myNewFacetTypes.isEmpty()) {
      return null;
    }

    return myAutodetectingManager.getFileTypes(myNewFacetTypes);
  }

  protected void writeHeader(final DataOutputStream output) throws IOException {
    FacetType[] types = FacetTypeRegistry.getInstance().getFacetTypes();
    output.writeInt(types.length);
    for (FacetType type : types) {
      output.writeUTF(type.getStringId());
    }
  }

  public void queueEntryUpdate(final VirtualFile file) {
    myAutodetectingManager.queueUpdate(file);
  }

  protected void doUpdateIndexEntry(final VirtualFile file) {
    myAutodetectingManager.processFile(file);
  }

  @Nullable
  public Set<String> getFiles(Integer id) {
    return myDetectedFacetIds.getKeys(id); 
  }

  @Nullable
  public Set<String> getFiles(final FacetPointer pointer) {
    return myFacets.getKeys(pointer);
  }

  @Nullable
  public Set<String> getFiles(final Facet facet) {
    return myFacets.getKeys(myFacetPointersManager.create(facet));
  }

  protected void onEntryAdded(final String url, final FacetDetectionIndexEntry entry) {
    myFacets.removeKey(url);
    SmartList<FacetPointer> detectedFacets = entry.getFacets();
    if (detectedFacets != null) {
      for (FacetPointer detectedFacet : detectedFacets) {
        myFacets.put(url, detectedFacet);
      }
    }
    myDetectedFacetIds.removeKey(url);
    SmartList<Integer> facetIds = entry.getDetectedFacetIds();
    if (facetIds != null) {
      for (Integer id : facetIds) {
        myDetectedFacetIds.put(url, id);
      }
    }
  }

  protected void onEntryRemoved(final String url, final FacetDetectionIndexEntry entry) {
    Set<Integer> ids = myDetectedFacetIds.getValues(url);
    myDetectedFacetIds.removeKey(url);
    if (ids != null && !ids.isEmpty()) {
      myAutodetectingManager.removeObsoleteFacets(ids);
    }
  }

  public void removeFacetFromCache(final FacetPointer<Facet> facetPointer) {
    Set<String> urls = myFacets.getKeys(facetPointer);
    if (urls != null) {
      for (String url : urls) {
        FacetDetectionIndexEntry indexEntry = getIndexEntry(url);
        if (indexEntry != null) {
          indexEntry.remove(facetPointer);
        }
      }
    }
    myFacets.removeValue(facetPointer);
  }

  public void updateIndexEntryForCreatedFacet(final DetectedFacetInfo<Module> info, final Facet facet) {
    FacetPointer<Facet> pointer = FacetPointersManager.getInstance(facet.getModule().getProject()).create(facet);
    Set<String> urls = myDetectedFacetIds.getKeys(info.getId());
    if (urls != null) {
      String[] urlsArray = ArrayUtil.toStringArray(urls);
      for (String url : urlsArray) {
        myDetectedFacetIds.remove(url, info.getId());
        myFacets.put(url, pointer);
        FacetDetectionIndexEntry indexEntry = getIndexEntry(url);
        indexEntry.remove(info.getId());
        indexEntry.add(pointer);
      }
    }
  }

  public boolean isEmpty() {
    return myDetectedFacetIds.isEmpty() && myFacets.isEmpty();
  }

  public void removeFromIndex(final DetectedFacetInfo<Module> info) {
    int id = info.getId();
    Set<String> urls = myDetectedFacetIds.getKeys(id);
    if (urls != null) {
      for (String url : urls) {
        FacetDetectionIndexEntry entry = getIndexEntry(url);
        if (entry != null) {
          entry.remove(id);
        }
      }
    }
    myDetectedFacetIds.removeValue(id);
  }
}
