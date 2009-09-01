package com.intellij.rt.compiler;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Vector;

/**
 * MUST BE COMPILED WITH JDK 1.1 IN ORDER TO SUPPORT JAVAC LAUNCHING FOR ALL JDKs
 */
public class JavacRunner {

  /**
   * @param args - params
   *  0. jdk version string
   *  1. javac main class
   *  2. javac parameters
   */
  public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException, IOException {

    if (!JavacResourcesReader.dumpPatterns()) {
      return;
    }

    final String versionString = args[0];
    final Class aClass = Class.forName(args[1]);
    //noinspection HardCodedStringLiteral
    final Method mainMethod = aClass.getMethod("main", new Class[] {String[].class});
    String[] newArgs;
    if (versionString.indexOf("1.1") > -1) {
      // expand the file
      final Vector arguments = new Vector();
      boolean isClasspath = false;
      for (int idx = 3; idx < args.length; idx++) {
        final String arg = args[idx];
        if (arg.startsWith("@") && !isClasspath) {
          String path = arg.substring(1);
          addFilesToCompile(arguments, path);
        }
        else {
          isClasspath = "-classpath".equals(arg) || "-cp".equals(arg) || "-bootclasspath".equals(arg);
          arguments.addElement(arg);
        }
      }
      newArgs = new String[arguments.size()];
      for (int idx = 0; idx < newArgs.length; idx++) {
        newArgs[idx] = (String)arguments.elementAt(idx);
      }
    }
    else {
      newArgs = new String[args.length - 2];
      System.arraycopy(args, 2, newArgs, 0, newArgs.length);
    }
    expandClasspath(newArgs);
    mainMethod.invoke(null, new Object[] {newArgs});
  }

  private static void addFilesToCompile(Vector arguments, String path) throws IOException {
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(new File(path)));
      for (String filePath = reader.readLine(); filePath != null; filePath = reader.readLine()) {
        arguments.addElement(filePath.replace('/', File.separatorChar));
      }
    }
    finally {
      if (reader != null) {
        reader.close();
      }
    }
  }

  private static void expandClasspath(String[] args) throws IOException {
    for (int idx = 0; idx < args.length; idx++) {
      final String arg = args[idx];
      //noinspection HardCodedStringLiteral
      if ("-classpath".equals(arg) || "-cp".equals(arg) || "-bootclasspath".equals(arg)) {
        final String cpValue = args[idx + 1];
        if (cpValue.startsWith("@")) {
          args[idx + 1] = readClasspath(cpValue.substring(1));
        }
      }
    }
  }

  public static String readClasspath(String filePath) throws IOException {
    final DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(new File(filePath))));
    try {
      return readString(in);
    }
    finally {
      in.close();
    }
  }

  private static String readString(DataInput stream) throws IOException {
    int length = stream.readInt();
    if (length == -1) return null;

    char[] chars = new char[length];
    byte[] bytes = new byte[length*2];
    stream.readFully(bytes);

    for (int i = 0, i2 = 0; i < length; i++, i2+=2) {
      chars[i] = (char)((bytes[i2] << 8) + (bytes[i2 + 1] & 0xFF));
    }

    return new String(chars);
  }

}
