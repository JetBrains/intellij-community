/**
 * created at Jan 17, 2002
 * @author Jeka
 */
package com.intellij.compiler.make;

import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.classParsing.*;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.cls.ClsUtil;
import org.jetbrains.annotations.NonNls;

public class MakeUtil {

  private MakeUtil() {
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.MakeUtil");


  public static VirtualFile getSourceRoot(CompileContext context, Module module, VirtualFile file) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
    final VirtualFile root = fileIndex.getSourceRootForFile(file);
    if (root != null) {
      return root;
    }
    // try to find among roots of generated files.
    final VirtualFile[] sourceRoots = context.getSourceRoots(module);
    for (final VirtualFile sourceRoot : sourceRoots) {
      if (fileIndex.isInSourceContent(sourceRoot)) {
        continue; // skip content source roots, need only roots for generated files
      }
      if (VfsUtil.isAncestor(sourceRoot, file, false)) {
        return sourceRoot;
      }
    }
    return null;
  }

  /**
   * cuts inner or anonymous class' parts and translates package names to lower case
   */
  private static String normalizeClassName(String qName) {
    int index = qName.indexOf('$');
    if (index >= 0) {
      qName = qName.substring(0, index);
    }
    if (SystemInfo.isFileSystemCaseSensitive) {
      return qName;
    }
    // the name of a dir should be lowercased because javac seem to allow difference in case
    // between the physical directory and package name.
    final int dotIndex = qName.lastIndexOf('.');
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(qName);
      for (int idx = 0; idx < dotIndex; idx++) {
        builder.setCharAt(idx, Character.toLowerCase(builder.charAt(idx)));
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public static boolean isAnonymous(String name) {
    int index = name.lastIndexOf('$');
    if (index >= 0) {
      index++;
      if (index < name.length()) {
        try {
          Integer.parseInt(name.substring(index));
          return true;
        }
        catch (NumberFormatException e) {
        }
      }
    }
    return false;
  }

  /*
     not needed currently
  public static String getEnclosingClassName(String anonymousQName) {
    return anonymousQName.substring(0, anonymousQName.lastIndexOf('$'));
  }
  */

  /*
   not needed currently
  public static boolean isNative(int flags) {
    return (ClsUtil.ACC_NATIVE & flags) != 0;
  }
  */

  /**
   * tests if the accessibility, denoted by flags1 is less restricted than the accessibility denoted by flags2
   * @return true means flags1 is less restricted than flags2 <br>
   *         false means flags1 define more restricted access than flags2 or they have equal accessibility
   */
  public static boolean isMoreAccessible(int flags1, int flags2) {
    if (ClsUtil.isPrivate(flags2)) {
      return ClsUtil.isPackageLocal(flags1) || ClsUtil.isProtected(flags1) || ClsUtil.isPublic(flags1);
    }
    if (ClsUtil.isPackageLocal(flags2)) {
      return ClsUtil.isProtected(flags1) || ClsUtil.isPublic(flags1);
    }
    if (ClsUtil.isProtected(flags2)) {
      return ClsUtil.isPublic(flags1);
    }
    return false;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String relativeClassPathToQName(String relativePath, char separator) {
    int start = 0;
    int end = relativePath.length() - ".class".length();
    if (relativePath.startsWith(String.valueOf(separator))) {
      start += 1;
    }
    return (start <= end)? relativePath.substring(start, end).replace(separator, '.') : null;
  }

  public static @NonNls String parseObjectType(final String descriptor, int fromIndex) {
    int semicolonIndex = descriptor.indexOf(';', fromIndex);
    if (descriptor.charAt(fromIndex) == 'L' && semicolonIndex > fromIndex) { // isObjectType
      return descriptor.substring(fromIndex + 1, semicolonIndex).replace('/', '.');
    }
    if (descriptor.charAt(fromIndex) == '[' && (descriptor.length() - fromIndex) > 0) { // isArrayType
      return parseObjectType(descriptor, fromIndex + 1);
    }
    return null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isPrimitiveType(String descriptor) {
    return
      "V".equals(descriptor) ||
      "B".equals(descriptor) ||
      "C".equals(descriptor) ||
      "D".equals(descriptor) ||
      "F".equals(descriptor) ||
      "I".equals(descriptor) ||
      "J".equals(descriptor) ||
      "S".equals(descriptor) ||
      "Z".equals(descriptor);
  }

  public static boolean isArrayType(String descriptor) {
    return StringUtil.startsWithChar(descriptor, '[');
  }

  public static String getComponentType(String descriptor) {
    if (!isArrayType(descriptor)) {
      return null;
    }
    return descriptor.substring(1);
  }


  /**
   * @return a normalized path to source relative to a source root by class qualified name and sourcefile short name.
   *  The path uses forward slashes "/".
   */
  public static String createRelativePathToSource(String qualifiedName, String srcName) {
    qualifiedName = normalizeClassName(qualifiedName);
    int index = qualifiedName.lastIndexOf('.');
    if (index >= 0) {
      srcName = qualifiedName.substring(0, index).replace('.', '/') + "/" + srcName;
    }
    return srcName;
  }

  public static boolean isInterface(int flags) {
    return (ClsUtil.ACC_INTERFACE & flags) != 0;
  }

  public static int getAnnotationTargets(final Cache cache, final int annotationQName, final SymbolTable symbolTable) throws CacheCorruptedException {
    final AnnotationConstantValue targetAnnotation = findAnnotation(
      "java.lang.annotation.Target",
      cache.getRuntimeVisibleAnnotations(cache.getClassId(annotationQName)), symbolTable);
    if (targetAnnotation == null) {
      return AnnotationTargets.ALL; // all program elements are annotation targets by default
    }
    final AnnotationNameValuePair[] memberValues = targetAnnotation.getMemberValues();
    ConstantValueArray value = (ConstantValueArray)memberValues[0].getValue();
    final ConstantValue[] targets = value.getValue();
    int annotationTargets = 0;
    for (final ConstantValue target : targets) {
      if (target instanceof EnumConstantValue) {
        final String constantName = symbolTable.getSymbol(((EnumConstantValue)target).getConstantName());
        if (AnnotationTargets.TYPE_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.TYPE;
        }
        if (AnnotationTargets.FIELD_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.FIELD;
        }
        if (AnnotationTargets.METHOD_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.METHOD;
        }
        if (AnnotationTargets.PARAMETER_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.PARAMETER;
        }
        if (AnnotationTargets.CONSTRUCTOR_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.CONSTRUCTOR;
        }
        if (AnnotationTargets.LOCAL_VARIABLE_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.LOCAL_VARIABLE;
        }
        if (AnnotationTargets.ANNOTATION_TYPE_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.ANNOTATION_TYPE;
        }
        if (AnnotationTargets.PACKAGE_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.PACKAGE;
        }
      }
    }
    return annotationTargets;
  }

  public static int getAnnotationRetentionPolicy(final int annotationQName, final Cache cache, final SymbolTable symbolTable) throws CacheCorruptedException {
    final AnnotationConstantValue retentionPolicyAnnotation = findAnnotation(
      "java.lang.annotation.Retention",
      cache.getRuntimeVisibleAnnotations(cache.getClassId(annotationQName)), symbolTable
    );
    if (retentionPolicyAnnotation == null) {
      return RetentionPolicies.CLASS; // default retention policy
    }
    final AnnotationNameValuePair[] memberValues = retentionPolicyAnnotation.getMemberValues();
    final EnumConstantValue value = (EnumConstantValue)memberValues[0].getValue();
    final String constantName = symbolTable.getSymbol(value.getConstantName());
    if (RetentionPolicies.SOURCE_STR.equals(constantName)) {
      return RetentionPolicies.SOURCE;
    }
    if (RetentionPolicies.CLASS_STR.equals(constantName)) {
      return RetentionPolicies.CLASS;
    }
    if (RetentionPolicies.RUNTIME_STR.equals(constantName)) {
      return RetentionPolicies.RUNTIME;
    }
    LOG.error("Unknown retention policy: " + constantName);
    return -1;
  }

  public static AnnotationConstantValue findAnnotation(@NonNls final String annotationQName,
                                                       AnnotationConstantValue[] annotations, final SymbolTable symbolTable) throws CacheCorruptedException {
    for (final AnnotationConstantValue annotation : annotations) {
      if (annotationQName.equals(symbolTable.getSymbol(annotation.getAnnotationQName()))) {
        return annotation;
      }
    }
    return null;
  }

}
