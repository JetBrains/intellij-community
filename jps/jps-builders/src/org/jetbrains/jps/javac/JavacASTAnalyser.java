/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.javac;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *
 */
@SupportedAnnotationTypes("*")
public class JavacASTAnalyser extends AbstractProcessor{
  private Trees myTrees;
  private final DiagnosticOutputConsumer myOutputConsumer;
  private final boolean mySuppressOtherProcessors;

  public JavacASTAnalyser(DiagnosticOutputConsumer outputConsumer, boolean suppressOtherProcessors) {
    myOutputConsumer = outputConsumer;
    mySuppressOtherProcessors = suppressOtherProcessors;
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    myTrees = Trees.instance(processingEnv);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    final Set<? extends Element> elements = roundEnv.getRootElements();
    for (Element element : elements) {
      if (!(element instanceof TypeElement)) {
        continue;
      }
      final TypeElement typeElement = (TypeElement)element;

      final ImportsCollector importsCollector = new ImportsCollector();
      importsCollector.scan(myTrees.getPath(typeElement).getParentPath().getLeaf(), myTrees);

      final Set<String> imports = importsCollector.getImports();
      final Set<String> staticImports = importsCollector.getStaticImports();

      if (!imports.isEmpty() || !staticImports.isEmpty()) {
        final String className = typeElement.getQualifiedName().toString();
        myOutputConsumer.registerImports(className, imports, staticImports);
      }
      break;
    }
    return mySuppressOtherProcessors;
  }

  private static class ImportsCollector extends TreeScanner<Object, Trees> {
    private Set<String> myImports = new HashSet<>();
    private Set<String> myStaticImports = new HashSet<>();

    public Set<String> getImports() {
      return myImports;
    }

    public Set<String> getStaticImports() {
      return myStaticImports;
    }

    public Object visitImport(ImportTree node, Trees trees) {
      final Tree identifier = node.getQualifiedIdentifier();
      final Set<String> container = node.isStatic()? myStaticImports : myImports;
      container.add(identifier.toString());
      return null;
    }

    public Object visitClass(ClassTree node, Trees trees) {
      return null;
    }

    //public void registerOverriddenMethod(TypeElement classElement, ExecutableElement method) {
    //  final Elements utils = myProcessingEnvironment.getElementUtils();
    //  final String qName = utils.getBinaryName(classElement).toString();
    //  List<MethodDescriptor> descriptors = myOverriddenMethods.get(qName);
    //  if (descriptors == null) {
    //    descriptors = new ArrayList<MethodDescriptor>();
    //    myOverriddenMethods.put(qName, descriptors);
    //  }
    //  final StringBuilder buf = new StringBuilder();
    //  buf.append("(");
    //  for (VariableElement param : method.getParameters()) {
    //    buf.append(getSignature(param.asType()));
    //  }
    //  buf.append(")").append(getSignature(method.getReturnType()));
    //  descriptors.add(new MethodDescriptor(method.getSimpleName().toString(), buf.toString()));
    //}

    //private static String getSignature(TypeMirror type) {
    //  switch (type.getKind()) {
    //    case BOOLEAN: return "Z";
    //    case BYTE: return "B";
    //    case CHAR: return "C";
    //    case SHORT: return "S";
    //    case INT: return "I";
    //    case LONG: return "J";
    //    case FLOAT: return "F";
    //    case DOUBLE: return "D";
    //    case VOID: return "V";
    //    case ARRAY:
    //      final String signature = getSignature(((ArrayType)type).getComponentType());
    //      return signature != null? "[" + signature : null;
    //    case DECLARED:
    //      final TypeElement typeElement = (TypeElement)((DeclaredType)type).asElement();
    //      final String qName = typeElement.getQualifiedName().toString().replace(".", "/");
    //      return "L" + qName + ";";
    //    default:
    //      return null;
    //  }
    //}
  }

  //private static class IdentifiersCollector extends TreeScanner<Object, Trees> {
  //  private Set<Name> myIdentifiers = new HashSet<Name>();
  //  private Set<String> myImports = new HashSet<String>();
  //  private Set<String> myStaticImports = new HashSet<String>();
  //
  //  public Set<String> getIdentifiers() {
  //    final HashSet<String> result = new HashSet<String>();
  //    for (Name name : myIdentifiers) {
  //      result.add(name.toString());
  //    }
  //    return result;
  //  }
  //
  //  @Override
  //  public Object visitImport(ImportTree node, Trees trees) {
  //    final Tree identifier = node.getQualifiedIdentifier();
  //    final Set<String> container = node.isStatic()? myStaticImports : myImports;
  //    container.add(identifier.toString());
  //    return null;
  //  }
  //
  //  @Override
  //  public Object visitAnnotation(AnnotationTree node, Trees trees) {
  //    return scan(node.getArguments(), trees);
  //  }
  //
  //  @Override
  //  public Object visitIdentifier(IdentifierTree node, Trees trees) {
  //    myIdentifiers.add(node.getName());
  //    return super.visitIdentifier(node, trees);
  //  }
  //
  //  @Override
  //  public Object visitMemberSelect(MemberSelectTree node, Trees trees) {
  //    myIdentifiers.add(node.getIdentifier());
  //    return scan(node.getExpression(), trees);
  //  }
  //
  //  @Override
  //  public Object visitClass(ClassTree node, Trees trees) {
  //    return scan(node.getMembers(), trees);
  //  }
  //
  //  @Override
  //  public Object visitVariable(VariableTree node, Trees trees) {
  //    return scan(node.getInitializer(), trees);
  //  }
  //
  //  @Override
  //  public Object visitMethod(MethodTree node, Trees trees) {
  //    return scan(node.getBody(), trees);
  //  }
  //
  //  @Override
  //  public Object visitMethodInvocation(MethodInvocationTree node, Trees trees) {
  //    return scan(node.getArguments(), trees);
  //  }
  //
  //  @Override
  //  public Object visitTypeCast(TypeCastTree node, Trees trees) {
  //    return scan(node.getExpression(), trees);
  //  }
  //
  //  @Override
  //  public Object visitInstanceOf(InstanceOfTree node, Trees trees) {
  //    return scan(node.getExpression(), trees);
  //  }
  //}
}
