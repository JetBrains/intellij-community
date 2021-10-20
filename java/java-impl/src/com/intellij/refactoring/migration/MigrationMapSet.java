
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.migration;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class MigrationMapSet {
  private static final Logger LOG = Logger.getInstance(MigrationMapSet.class);

  private ArrayList<MigrationMap> myMaps;
  @NonNls private static final String MIGRATION_MAP = "migrationMap";
  @NonNls private static final String ENTRY = "entry";
  @NonNls private static final String NAME = "name";
  @NonNls private static final String OLD_NAME = "oldName";
  @NonNls private static final String NEW_NAME = "newName";
  @NonNls private static final String DESCRIPTION = "description";
  @NonNls private static final String ORDER = "order";
  @NonNls private static final String VALUE = "value";
  @NonNls private static final String TYPE = "type";
  @NonNls private static final String PACKAGE_TYPE = "package";
  @NonNls private static final String CLASS_TYPE = "class";
  @NonNls private static final String RECURSIVE = "recursive";

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
  }

  public static boolean isPredefined(String name) {
    for (PredefinedMigrationProvider provider : PredefinedMigrationProvider.EP_NAME.getExtensionList()) {
      URL migrationMap = provider.getMigrationMap();
      String fileName = FileUtilRt.getNameWithoutExtension(new File(migrationMap.getFile()).getName());
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

  @Nullable
  private static File getMapDirectory() {
    Path dir = PathManager.getConfigDir().resolve("migration");
    try {
      Files.createDirectories(dir);
    }
    catch (IOException e) {
      LOG.error("cannot create directory: " + dir, e);
      return null;
    }
    return dir.toFile();
  }

  private static void copyPredefinedMaps(File dir) {
    for (PredefinedMigrationProvider provider : PredefinedMigrationProvider.EP_NAME.getExtensionList()) {
      URL migrationMap = provider.getMigrationMap();
      String fileName = new File(migrationMap.getFile()).getName();
      copyMap(dir, migrationMap, fileName);
    }
  }

  private static void copyMap(File dir, URL url, String fileName) {
    File targetFile = new File(dir, fileName);
    if (targetFile.isFile()) return;

    try (FileOutputStream outputStream = new FileOutputStream(targetFile); InputStream inputStream = url.openStream()) {
      FileUtil.copy(inputStream, outputStream);
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
    for (File file : files) {
      try {
        MigrationMap map = readMap(file);
        if (map != null) {
          map.setFileName(FileUtilRt.getNameWithoutExtension(file.getName()));
          myMaps.add(map);
        }
      }
      catch (InvalidDataException | JDOMException e) {
        LOG.error("Invalid data in file: " + file.getAbsolutePath());
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    myMaps.sort((o1, o2) -> Integer.compare(o2.getOrder(), o1.getOrder()));
  }

  private static MigrationMap readMap(File file) throws JDOMException, InvalidDataException, IOException {
    if (!file.exists()) {
      return null;
    }

    Element root = JDOMUtil.load(file);
    if (!MIGRATION_MAP.equals(root.getName())){
      throw new InvalidDataException();
    }
    String fileName = file.getName();

    MigrationMap map = new MigrationMap();

    for (Element node : root.getChildren()) {
      if (NAME.equals(node.getName())) {
        @NlsSafe String name = node.getAttributeValue(VALUE);
        map.setName(name);
        for (PredefinedMigrationProvider provider : PredefinedMigrationProvider.EP_NAME.getExtensionList()) {
          if (new File(provider.getMigrationMap().getFile()).getName().equals(fileName)) {
            map.setDescription(provider.getDescription());
            break;
          }
        }
      }

      if (map.getDescription() == null && DESCRIPTION.equals(node.getName())) {
        @NlsSafe String description = node.getAttributeValue(VALUE);
        map.setDescription(description);
      }

      if (ORDER.equals(node.getName())) {
        String orderValue = node.getAttributeValue(VALUE);
        if (orderValue != null && !orderValue.isBlank()) {
          map.setOrder(Integer.valueOf(orderValue));
        }
      }

      if (ENTRY.equals(node.getName())) {
        MigrationMapEntry entry = new MigrationMapEntry();
        String oldName = node.getAttributeValue(OLD_NAME);
        if (oldName == null) {
          throw new InvalidDataException();
        }
        entry.setOldName(oldName);
        String newName = node.getAttributeValue(NEW_NAME);
        if (newName == null) {
          throw new InvalidDataException();
        }
        entry.setNewName(newName);
        String typeStr = node.getAttributeValue(TYPE);
        if (typeStr == null) {
          throw new InvalidDataException();
        }
        entry.setType(MigrationMapEntry.CLASS);
        if (typeStr.equals(PACKAGE_TYPE)) {
          entry.setType(MigrationMapEntry.PACKAGE);
          @NonNls String isRecursiveStr = node.getAttributeValue(RECURSIVE);
          if (isRecursiveStr != null && isRecursiveStr.equals("true")) {
            entry.setRecursive(true);
          }
          else {
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
  }

  private static Document saveMap(MigrationMap map) {
    Element root = new Element(MIGRATION_MAP);

    Element nameElement = new Element(NAME);
    nameElement.setAttribute(VALUE, map.getName());
    root.addContent(nameElement);

    Element orderElement = new Element(ORDER);
    orderElement.setAttribute(VALUE, String.valueOf(map.getOrder()));
    root.addContent(orderElement);

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
