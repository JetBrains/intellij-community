package com.intellij.openapi.editor;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Aug 13, 2004
 * Time: 9:55:12 PM
 * To change this template use File | Settings | File Templates.
 */
public interface EditorGutter {
  void registerTextAnnotation(TextAnnotationGutterProvider provider);

  void closeAllAnnotations();
}
