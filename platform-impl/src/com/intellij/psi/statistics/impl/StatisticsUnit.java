package com.intellij.psi.statistics.impl;

import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

class StatisticsUnit {
  private static final int FORMAT_VERSION_NUMBER = 5;

  private final int myNumber;

  private final THashMap<String, List<String>> myDataMap = new THashMap<String, List<String>>();

  public StatisticsUnit(int number) {
    myNumber = number;
  }

  public int getData(String key1, String key2) {
    final List<String> list = myDataMap.get(key1);
    if (list == null) return 0;

    int result = 0;
    for (final String s : list) {
      if (s.equals(key2)) result++;
    }
    return result;
  }

  public void incData(String key1, String key2) {
    List<String> list = myDataMap.get(key1);
    if (list == null) {
      myDataMap.put(key1, list = new ArrayList<String>());
    }
    list.add(key2);
    if (list.size() > StatisticsManager.OBLIVION_THRESHOLD) {
      list.remove(0);
    }
  }

  public String[] getKeys2(final String key1){
    final List<String> list = myDataMap.get(key1);
    if (list == null) return ArrayUtil.EMPTY_STRING_ARRAY;

    final HashSet<String> keys = new HashSet<String>(list);
    return keys.toArray(new String[keys.size()]);
  }

  public int getNumber() {
    return myNumber;
  }

  public void write(OutputStream out) throws IOException{
    final DataOutputStream dataOut = new DataOutputStream(out);
    dataOut.writeInt(FORMAT_VERSION_NUMBER);

    dataOut.writeInt(myDataMap.size());
    for (final String context : myDataMap.keySet()) {
      final List<String> list = myDataMap.get(context);
      if (list != null && !list.isEmpty()) {
        dataOut.writeUTF(context);
        dataOut.writeInt(list.size());
        for (final String data : list) {
          dataOut.writeUTF(data);
        }
      }
    }
  }

  public void read(InputStream in) throws IOException, WrongFormatException {
    DataInputStream dataIn = new DataInputStream(in);
    int formatVersion = dataIn.readInt();
    if (formatVersion != FORMAT_VERSION_NUMBER){
      throw new WrongFormatException();
    }

    myDataMap.clear();
    int size = dataIn.readInt();
    for(int i = 0; i < size; i++){
      String context = dataIn.readUTF();
      int len = dataIn.readInt();
      List<String> list = new ArrayList<String>(len);
      for (int j = 0; j < len; j++) {
        list.add(dataIn.readUTF());
      }
      myDataMap.put(context, list);
    }
  }

}