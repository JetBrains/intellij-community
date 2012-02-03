package org.jetbrains.jps.javac;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 2/1/12
 *
 */
//@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes("*")
public class JavacASTAnalyser extends AbstractProcessor{

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getRootElements()) {
      System.out.println("Element: " + element.getSimpleName());
    }
    return false;
  }
}
