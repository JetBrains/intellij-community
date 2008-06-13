package com.intellij.psi.statistics.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.reference.SoftReference;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ScrambledInputStream;
import com.intellij.util.ScrambledOutputStream;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;

public class StatisticsManagerImpl extends StatisticsManager {
  private static final int UNIT_COUNT = 997;

  @NonNls private static final String STORE_PATH = PathManager.getSystemPath() + File.separator + "stat";

  private final SoftReference[] myUnits = new SoftReference[UNIT_COUNT];
  private final HashSet<StatisticsUnit> myModifiedUnits = new HashSet<StatisticsUnit>();

  public int getUseCount(@NotNull final StatisticsInfo info) {
    if (info == StatisticsInfo.EMPTY) return 0;

    String key1 = info.getContext();
    int unitNumber = getUnitNumber(key1);
    StatisticsUnit unit = getUnit(unitNumber);
    return unit.getData(key1, info.getValue());
  }

  public void incUseCount(@NotNull final StatisticsInfo info) {
    if (info == StatisticsInfo.EMPTY) return;

    final String key1 = info.getContext();
    int unitNumber = getUnitNumber(key1);
    StatisticsUnit unit = getUnit(unitNumber);
    unit.incData(key1, info.getValue());
    myModifiedUnits.add(unit);
  }

  public StatisticsInfo[] getAllValues(final String context) {
    final String[] strings = getUnit(getUnitNumber(context)).getKeys2(context);
    return ContainerUtil.map2Array(strings, StatisticsInfo.class, new NotNullFunction<String, StatisticsInfo>() {
      @NotNull
      public StatisticsInfo fun(final String s) {
        return new StatisticsInfo(context, s);
      }
    });
  }

  public void save() {
    if (!ApplicationManager.getApplication().isUnitTestMode()){
      for (StatisticsUnit unit : myModifiedUnits) {
        saveUnit(unit.getNumber());
      }
    }
    myModifiedUnits.clear();
  }

  private StatisticsUnit getUnit(int unitNumber) {
    SoftReference ref = myUnits[unitNumber];
    if (ref != null){
      StatisticsUnit unit = (StatisticsUnit)ref.get();
      if (unit != null) return unit;
    }
    StatisticsUnit unit = loadUnit(unitNumber);
    if (unit == null){
      unit = new StatisticsUnit(unitNumber);
    }
    myUnits[unitNumber] = new SoftReference<StatisticsUnit>(unit);
    return unit;
  }

  private static StatisticsUnit loadUnit(int unitNumber) {
    StatisticsUnit unit = new StatisticsUnit(unitNumber);
    if (!ApplicationManager.getApplication().isUnitTestMode()){
      String path = getPathToUnit(unitNumber);
      try{
        InputStream in = new BufferedInputStream(new FileInputStream(path));
        in = new ScrambledInputStream(in);
        try{
          unit.read(in);
        }
        finally{
          in.close();
        }
      }
      catch(IOException e){
      }
      catch(WrongFormatException e){
      }
    }
    return unit;
  }

  private void saveUnit(int unitNumber){
    if (!createStoreFolder()) return;
    StatisticsUnit unit = getUnit(unitNumber);
    String path = getPathToUnit(unitNumber);
    try{
      OutputStream out = new BufferedOutputStream(new FileOutputStream(path));
      out = new ScrambledOutputStream(out);
      try {
        unit.write(out);
      }
      finally{
        out.close();
      }
    }
    catch(IOException e){
      Messages.showMessageDialog(
        IdeBundle.message("error.saving.statistics", e.getLocalizedMessage()),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
    }
  }

  private static int getUnitNumber(String key1) {
    return Math.abs(key1.hashCode()) % UNIT_COUNT;
  }

  private static boolean createStoreFolder(){
    File homeFile = new File(STORE_PATH);
    if (!homeFile.exists()){
      if (!homeFile.mkdirs()){
        Messages.showMessageDialog(
          IdeBundle.message("error.saving.statistic.failed.to.create.folder", STORE_PATH),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String getPathToUnit(int unitNumber) {
    return STORE_PATH + File.separator + "unit." + unitNumber;
  }

  @TestOnly
  public void clearStatistics() {
    Arrays.fill(myUnits, null);
  }
}