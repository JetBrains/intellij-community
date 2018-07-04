
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
package com.intellij.refactoring.migration;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

public class MigrationMapSet {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.migration.MigrationMapSet");

  private ArrayList<MigrationMap> myMaps;
  @NonNls private static final String MIGRATION_MAP = "migrationMap";
  @NonNls private static final String ENTRY = "entry";
  @NonNls private static final String NAME = "name";
  @NonNls private static final String OLD_NAME = "oldName";
  @NonNls private static final String NEW_NAME = "newName";
  @NonNls private static final String DESCRIPTION = "description";
  @NonNls private static final String VALUE = "value";
  @NonNls private static final String TYPE = "type";
  @NonNls private static final String PACKAGE_TYPE = "package";
  @NonNls private static final String CLASS_TYPE = "class";
  @NonNls private static final String RECURSIVE = "recursive";

  @NonNls private static final String[] DEFAULT_MAPS = new  String[] {
    "/com/intellij/refactoring/migration/res/Swing__1_0_3____1_1_.xml",
  };
  private final Set<String> myDeletedMaps = new TreeSet<>();

  public MigrationMapSet() {
  }

  public void addMap(MigrationMap map) {
    if (myMaps == null){
      loadMaps();
    }
    myMaps.add(map);
//    saveMaps();
  }

  @Nullable
  public MigrationMap findMigrationMap(@NotNull String name) {
    if (myMaps == null) {
      loadMaps();
    }
    for (MigrationMap map : myMaps) {
      if (name.equals(map.getName())) {
        return map;
      }
    }
    return null;
  }

  public void replaceMap(MigrationMap oldMap, MigrationMap newMap) {
    for(int i = 0; i < myMaps.size(); i++){
      if (myMaps.get(i) == oldMap){
        myMaps.set(i, newMap);
      }
    }
  }

  public void removeMap(MigrationMap map) {
    if (myMaps == null){
      loadMaps();
    }
    myMaps.remove(map);
    String name = map.getFileName();
    if (isPredefined(name)) {
      myDeletedMaps.add(name);
    }
  }

  private static boolean isPredefined(String name) {
    for (PredefinedMigrationProvider provider : Extensions.getExtensions(PredefinedMigrationProvider.EP_NAME)) {
      URL migrationMap = provider.getMigrationMap();
      String fileName = FileUtil.getNameWithoutExtension(new File(migrationMap.getFile()));
      if (fileName.equals(name)) return true;
    }

    for (String defaultTemplate : DEFAULT_MAPS) {
      String fileName = FileUtil.getNameWithoutExtension(StringUtil.getShortName(defaultTemplate, '/'));

      if (fileName.equals(name)) return true;
    }
    return false;
  }

  public MigrationMap[] getMaps() {
    if (myMaps == null){
      loadMaps();
    }
    MigrationMap[] ret = new MigrationMap[myMaps.size()];
    for(int i = 0; i < myMaps.size(); i++){
      ret[i] = myMaps.get(i);
    }
    return ret;
  }

  private static File getMapDirectory() {
    File dir = new File(PathManager.getConfigPath() + File.separator + "migration");

    if (!dir.exists() && !dir.mkdir()) {
      LOG.error("cannot create directory: " + dir.getAbsolutePath());
      return null;
    }

    return dir;
  }

  private void copyPredefinedMaps(File dir) {
    File deletedFiles = new File(dir, "deleted.txt");
    if (deletedFiles.isFile()) {
      try {
        myDeletedMaps.addAll(Arrays.asList(FileUtil.loadFile(deletedFiles, true).split("\n")));
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    for (PredefinedMigrationProvider provider : Extensions.getExtensions(PredefinedMigrationProvider.EP_NAME)) {
      URL migrationMap = provider.getMigrationMap();
      String fileName = new File(migrationMap.getFile()).getName();
      if (myDeletedMaps.contains(FileUtil.getNameWithoutExtension(fileName))) continue;
      copyMap(dir, migrationMap, fileName);
    }

    for (String defaultTemplate : DEFAULT_MAPS) {
      URL url = MigrationMapSet.class.getResource(defaultTemplate);
      LOG.assertTrue(url != null);
      String fileName = defaultTemplate.substring(defaultTemplate.lastIndexOf("/") + 1);
      if (myDeletedMaps.contains(FileUtil.getNameWithoutExtension(fileName))) continue;
      copyMap(dir, url, fileName);
    }
  }

  private static void copyMap(File dir, URL url, String fileName) {
    File targetFile = new File(dir, fileName);
    if (targetFile.isFile()) return;

    try {
      FileOutputStream outputStream = new FileOutputStream(targetFile);
      InputStream inputStream = url.openStream();

      try {
        FileUtil.copy(inputStream, outputStream);
      }
      finally {
        outputStream.close();
        inputStream.close();
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private static File[] getMapFiles(final File dir) {
    if (dir == null) {
      return new File[0];
    }
    File[] ret = dir.listFiles(FileFilters.filesWithExtension("xml"));
    if (ret == null) {
      LOG.error("cannot read directory: " + dir.getAbsolutePath());
      return new File[0];
    }
    return ret;
  }

  private void loadMaps() {
    myMaps = new ArrayList<>();


    File dir = getMapDirectory();
    copyPredefinedMaps(dir);

    File[] files = getMapFiles(dir);
    for(int i = 0; i < files.length; i++){
      try{
        MigrationMap map = readMap(files[i]);
        if (map != null){
          map.setFileName(FileUtil.getNameWithoutExtension(files[i]));
          myMaps.add(map);
        }
      }
      catch(InvalidDataException | JDOMException e){
        LOG.error("Invalid data in file: " + files[i].getAbsolutePath());
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private static MigrationMap readMap(File file) throws JDOMException, InvalidDataException, IOException {
    if (!file.exists()) {
      return null;
    }

    Element root = JDOMUtil.load(file);
    if (!MIGRATION_MAP.equals(root.getName())){
      throw new InvalidDataException();
    }

    MigrationMap map = new MigrationMap();

    for(Iterator i = root.getChildren().iterator(); i.hasNext(); ){
      Element node = (Element)i.next();
      if (NAME.equals(node.getName())){
        String name = node.getAttributeValue(VALUE);
        map.setName(name);
      }
      if (DESCRIPTION.equals(node.getName())){
        String description = node.getAttributeValue(VALUE);
        map.setDescription(description);
      }

      if (ENTRY.equals(node.getName())){
        MigrationMapEntry entry = new MigrationMapEntry();
        String oldName = node.getAttributeValue(OLD_NAME);
        if (oldName == null){
          throw new InvalidDataException();
        }
        entry.setOldName(oldName);
        String newName = node.getAttributeValue(NEW_NAME);
        if (newName == null){
          throw new InvalidDataException();
        }
        entry.setNewName(newName);
        String typeStr = node.getAttributeValue(TYPE);
        if (typeStr == null){
          throw new InvalidDataException();
        }
        entry.setType(MigrationMapEntry.CLASS);
        if (typeStr.equals(PACKAGE_TYPE)){
          entry.setType(MigrationMapEntry.PACKAGE);
          @NonNls String isRecursiveStr = node.getAttributeValue(RECURSIVE);
          if (isRecursiveStr != null && isRecursiveStr.equals("true")){
            entry.setRecursive(true);
          }
          else{
            entry.setRecursive(false);
          }
        }
        map.addEntry(entry);
      }
    }

    return map;
  }

  public void saveMaps() throws IOException{
    File dir = getMapDirectory();
    if (dir == null) {
      return;
    }

    File[] files = getMapFiles(dir);

    @NonNls String[] filePaths = new String[myMaps.size()];
    Document[] documents = new Document[myMaps.size()];

    UniqueNameGenerator namesProvider = new UniqueNameGenerator();
    for(int i = 0; i < myMaps.size(); i++){
      MigrationMap map = myMaps.get(i);

      filePaths[i] = dir + File.separator + namesProvider.generateUniqueName(map.getFileName()) + ".xml";
      documents[i] = saveMap(map);
    }

    JDOMUtil.updateFileSet(files, filePaths, documents, CodeStyle.getDefaultSettings().getLineSeparator());

    if (!myDeletedMaps.isEmpty()) {
      FileUtil.writeToFile(new File(dir, "deleted.txt"), StringUtil.join(myDeletedMaps, "\n"));
    }
  }

  private static Document saveMap(MigrationMap map) {
    Element root = new Element(MIGRATION_MAP);

    Element nameElement = new Element(NAME);
    nameElement.setAttribute(VALUE, map.getName());
    root.addContent(nameElement);

    Element descriptionElement = new Element(DESCRIPTION);
    descriptionElement.setAttribute(VALUE, map.getDescription());
    root.addContent(descriptionElement);

    for(int i = 0; i < map.getEntryCount(); i++){
      MigrationMapEntry entry = map.getEntryAt(i);
      Element element = new Element(ENTRY);
      element.setAttribute(OLD_NAME, entry.getOldName());
      element.setAttribute(NEW_NAME, entry.getNewName());
      if (entry.getType() == MigrationMapEntry.PACKAGE){
        element.setAttribute(TYPE, PACKAGE_TYPE);
        element.setAttribute(RECURSIVE, Boolean.valueOf(entry.isRecursive()).toString());
      }
      else{
        element.setAttribute(TYPE, CLASS_TYPE);
      }
      root.addContent(element);
    }

    return new Document(root);
  }
}
