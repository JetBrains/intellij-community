package org.jetbrains.jps.javac;

import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
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
  private Trees myTrees;
  private final boolean mySuppressOtherProcessors;

  public JavacASTAnalyser(boolean suppressOtherProcessors) {
    mySuppressOtherProcessors = suppressOtherProcessors;
  }

  @Override
  public void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    myTrees = Trees.instance(processingEnv);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    MyAnalyzer scaner = new MyAnalyzer();
    for (Element element : roundEnv.getRootElements()) {
      final Tree tree = myTrees.getTree(element);
      scaner.scan(tree, myTrees);
    }

    return mySuppressOtherProcessors;
  }


  private static class MyAnalyzer extends TreeScanner<Object, Trees> {
    @Override
    public Object visitImport(ImportTree node, Trees trees) {
      return null/*super.visitImport(node, trees)*/;
    }

    @Override
    public Object visitClass(ClassTree node, Trees trees) {
      return scan(node.getMembers(), trees);
    }

    @Override
    public Object visitVariable(VariableTree node, Trees trees) {
      final ModifiersTree modifiers = node.getModifiers();
      final Set<Modifier> flags = modifiers.getFlags();
      if (flags.contains(Modifier.STATIC) && flags.contains(Modifier.FINAL)) {
        final Name variableName = node.getName();
        // todo register constant
        final ConstantRefsFinder finder = new ConstantRefsFinder();
        finder.scan(node.getInitializer(), trees);
        // todo: process found refs
      }
      return null;
    }

    @Override
    public Object visitMethod(MethodTree node, Trees trees) {
      final ConstantRefsFinder finder = new ConstantRefsFinder();
      finder.scan(node.getBody(), trees);
      // todo: process found refs
      return null;
    }
  }

  private static class ConstantRefsFinder extends TreeScanner<Object, Trees> {
    @Override
    public Object visitMethodInvocation(MethodInvocationTree node, Trees trees) {
      return scan(node.getArguments(), trees);
    }

    @Override
    public Object visitMemberSelect(MemberSelectTree node, Trees trees) {
      return super.visitMemberSelect(node, trees);
    }

    @Override
    public Object visitIdentifier(IdentifierTree node, Trees trees) {
      return super.visitIdentifier(node, trees);
    }
  }

}
