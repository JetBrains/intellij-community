package com.intellij.compiler.make;

import com.intellij.compiler.SymbolTable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.cls.ClsUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;

import java.util.ArrayList;

/**
 * @author Eugene Zhuravlev
 * Date: Aug 18, 2003
 * Time: 6:32:32 PM
 */
public class CacheUtils {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.CacheUtils");

  public static String getMethodReturnTypeDescriptor(final Cache cache, final int methodDeclarationId, final SymbolTable symbolTable) throws CacheCorruptedException {
    String descriptor = symbolTable.getSymbol(cache.getMethodDescriptor(methodDeclarationId));
    return descriptor.substring(descriptor.indexOf(')') + 1, descriptor.length());
  }

  public static String getMethodGenericSignature(final Cache cache, final int methodDeclarationId, final SymbolTable symbolTable) throws CacheCorruptedException {
    final int methodGenericSignature = cache.getMethodGenericSignature(methodDeclarationId);
    if (methodGenericSignature == -1) {
      return null;
    }
    return symbolTable.getSymbol(methodGenericSignature);
  }

  public static String[] getParameterSignatures(Cache cache, int methodDeclarationId, SymbolTable symbolTable) throws CacheCorruptedException {
    String descriptor = symbolTable.getSymbol(cache.getMethodDescriptor(methodDeclarationId));
    int endIndex = descriptor.indexOf(')');
    if (endIndex <= 0) {
      LOG.assertTrue(false, "Corrupted method descriptor: "+descriptor);
    }
    return parseSignature(descriptor.substring(1, endIndex));
  }

  private static String[] parseSignature(String signature) {
    ArrayList list = new ArrayList();
    String paramSignature = parseParameterSignature(signature);
    while (paramSignature != null && !"".equals(paramSignature)) {
      list.add(paramSignature);
      signature = signature.substring(paramSignature.length());
      paramSignature = parseParameterSignature(signature);
    }
    return (String[])list.toArray(new String[list.size()]);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String parseParameterSignature(String signature) {
    if (StringUtil.startsWithChar(signature, 'B')) {
      return "B";
    }
    if (StringUtil.startsWithChar(signature, 'C')) {
      return "C";
    }
    if (StringUtil.startsWithChar(signature, 'D')) {
      return "D";
    }
    if (StringUtil.startsWithChar(signature, 'F')) {
      return "F";
    }
    if (StringUtil.startsWithChar(signature, 'I')) {
      return "I";
    }
    if (StringUtil.startsWithChar(signature, 'J')) {
      return "J";
    }
    if (StringUtil.startsWithChar(signature, 'S')) {
      return "S";
    }
    if (StringUtil.startsWithChar(signature, 'Z')) {
      return "Z";
    }
    if (StringUtil.startsWithChar(signature, 'L')) {
      return signature.substring(0, signature.indexOf(";") + 1);
    }
    if (StringUtil.startsWithChar(signature, '[')) {
      String s = parseParameterSignature(signature.substring(1));
      return (s != null) ? ("[" + s) : null;
    }
    return null;
  }

  public static int findField(final Cache cache, final int classDeclarationId, final int name, final int descriptor) throws CacheCorruptedException {
    final int[] fieldIds = cache.getFieldIds(classDeclarationId);
    for (int fieldId : fieldIds) {
      if (name != cache.getFieldName(fieldId)) {
        continue;
      }
      if (descriptor != cache.getFieldDescriptor(fieldId)) {
        continue;
      }
      return fieldId;
    }
    return Cache.UNKNOWN;
  }

  public static int findFieldByName(final Cache cache, final int classDeclarationId, final int name) throws CacheCorruptedException {
    final int[] fieldIds = cache.getFieldIds(classDeclarationId);
    for (int fieldId : fieldIds) {
      if (name != cache.getFieldName(fieldId)) {
        continue;
      }
      return fieldId;
    }
    return Cache.UNKNOWN;
  }

  public static int findMethod(final Cache cache, final int classDeclarationId, final int name, final int descriptor) throws CacheCorruptedException {
    final int[] methodIds = cache.getMethodIds(classDeclarationId);
    for (int methodId : methodIds) {
      if (name != cache.getMethodName(methodId)) {
        continue;
      }
      if (descriptor != cache.getMethodDescriptor(methodId)) {
        continue;
      }
      return methodId;
    }
    return Cache.UNKNOWN;
  }

  public static int[] findMethodsByName(final Cache cache, final int classDeclarationId, final int name) throws CacheCorruptedException {
    final int[] methodIds = cache.getMethodIds(classDeclarationId);
    TIntArrayList list = new TIntArrayList();
    for (final int methodId : methodIds) {
      if (name == cache.getMethodName(methodId)) {
        list.add(methodId);
      }
    }
    return list.toNativeArray();
  }

  public static int findMethodBySignature(final Cache cache, final int classDeclarationId, final String signature, SymbolTable symbolTable) throws CacheCorruptedException {
    final int[] methodIds = cache.getMethodIds(classDeclarationId);
    for (int methodId : methodIds) {
      final int name = cache.getMethodName(methodId);
      final int descriptor = cache.getMethodDescriptor(methodId);
      if (signature.equals(getMethodSignature(symbolTable.getSymbol(name), symbolTable.getSymbol(descriptor)))) {
        return methodId;
      }
    }
    return Cache.UNKNOWN;
  }

  public static String getMethodSignature(String name, String descriptor) {
    return name + descriptor.substring(0, descriptor.indexOf(')') + 1);
  }

  public static boolean areArraysContentsEqual(int[] exceptions1, int[] exceptions2) {
    if (exceptions1.length != exceptions2.length) {
      return false;
    }
    if (exceptions1.length != 0) { // optimization
      TIntHashSet exceptionsSet = new TIntHashSet(exceptions1);
      for (int exception : exceptions2) {
        if (!exceptionsSet.contains(exception)) {
          return false;
        }
      }
    }
    return true;
  }

  public static boolean isInterface(Cache cache, int classQName) throws CacheCorruptedException {
    final int classId = cache.getClassId(classQName);
    return MakeUtil.isInterface(cache.getFlags(classId));
  }

  public static boolean isAbstract(Cache cache, int classQName) throws CacheCorruptedException {
    final int classId = cache.getClassId(classQName);
    return ClsUtil.isAbstract(cache.getFlags(classId));
  }

  public static boolean isFinal(Cache cache, int classQName) throws CacheCorruptedException {
    final int classId = cache.getClassId(classQName);
    return ClsUtil.isFinal(cache.getFlags(classId));
  }

  public static final boolean isFieldReferenced(Cache cache, final int fieldId, final int referencerClassQName) throws CacheCorruptedException {
    final int[] referencers = cache.getFieldReferencers(fieldId);
    for (int referencer : referencers) {
      if (referencerClassQName == referencer) {
        return true;
      }
    }
    return false;
  }

  public static final boolean isMethodReferenced(Cache cache, final int methodId, final int referencerClassQName) throws CacheCorruptedException {
    final int[] referencers = cache.getMethodReferencers(methodId);
    for (final int referencer : referencers) {
      if (referencerClassQName == referencer) {
        return true;
      }
    }
    return false;
  }
}
