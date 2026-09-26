package mockapt;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A lightweight model of the JMH annotation processor's output behavior:
 * <ul>
 * <li>discovers annotated classes by iterating round root elements, like JMH's APGeneratorSource;
 * <li>for every top-level class with {@link Bench}-annotated methods generates a companion source file,
 *     passing the class as the originating element, like JMH's generated per-benchmark classes;
 * <li>in the final round writes two aggregate resources with NO originating elements:
 *     META-INF/MockList is merged with the previously existing content, replacing the entries
 *     of the classes processed in this session (like JMH's BenchmarkList), while META-INF/MockHints
 *     is overwritten with the current session's entries only (like JMH's CompilerHints).
 * </ul>
 */
@SupportedAnnotationTypes("mockapt.Bench")
public class MockBenchProcessor extends AbstractProcessor {
  private static final String LIST_RESOURCE = "META-INF/MockList";
  private static final String HINTS_RESOURCE = "META-INF/MockHints";

  // class FQN -> resource entry lines "classFqn#method=mode" contributed by the class in this session
  private final Map<String, List<String>> mySessionEntries = new TreeMap<String, List<String>>();

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      if (roundEnv.processingOver()) {
        writeResources();
      }
      else {
        generateSources(roundEnv);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return false;
  }

  private void generateSources(RoundEnvironment roundEnv) throws IOException {
    for (Element element : roundEnv.getRootElements()) { // root-element discovery, like JMH
      if (element.getKind() != ElementKind.CLASS || !(element instanceof TypeElement)) {
        continue;
      }
      TypeElement type = (TypeElement)element;
      String classFqn = type.getQualifiedName().toString();
      if (mySessionEntries.containsKey(classFqn)) {
        continue;
      }
      List<ExecutableElement> benchMethods = new ArrayList<ExecutableElement>();
      for (Element member : type.getEnclosedElements()) {
        if (member.getKind() == ElementKind.METHOD && member.getAnnotation(Bench.class) != null) {
          benchMethods.add((ExecutableElement)member);
        }
      }
      if (benchMethods.isEmpty()) {
        continue;
      }
      int lastDot = classFqn.lastIndexOf('.');
      String pkg = classFqn.substring(0, lastDot);
      String simpleName = type.getSimpleName().toString();
      List<String> entries = new ArrayList<String>();
      StringBuilder body = new StringBuilder();
      body.append("package ").append(pkg).append(".gen;\n\n");
      body.append("public final class ").append(simpleName).append("Gen {\n");
      for (ExecutableElement method : benchMethods) {
        String methodName = method.getSimpleName().toString();
        entries.add(classFqn + "#" + methodName + "=" + method.getAnnotation(Bench.class).value());
        // the chosen String.valueOf() overload depends on the method's return type,
        // so a changed return type produces different bytecode in the generated class, like in JMH's generated stubs
        body.append("  public static String call_").append(methodName).append("(").append(classFqn).append(" instance) {\n");
        body.append("    return String.valueOf(instance.").append(methodName).append("());\n");
        body.append("  }\n");
      }
      body.append("}\n");
      mySessionEntries.put(classFqn, entries);
      // the originating element IS passed for generated sources, like in JMH
      FileObject genSource = processingEnv.getFiler().createSourceFile(pkg + ".gen." + simpleName + "Gen", type);
      Writer writer = genSource.openWriter();
      try {
        writer.write(body.toString());
      }
      finally {
        writer.close();
      }
    }
  }

  private void writeResources() throws IOException {
    // MockList models META-INF/BenchmarkList: merge the previously existing content with the fresh entries,
    // replacing all entries of the classes processed in this session
    TreeSet<String> listLines = new TreeSet<String>();
    try {
      FileObject existing = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", LIST_RESOURCE);
      BufferedReader reader = new BufferedReader(new InputStreamReader(existing.openInputStream(), StandardCharsets.UTF_8));
      try {
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
          int hash = line.indexOf('#');
          String contributor = hash > 0? line.substring(0, hash) : "";
          if (!line.isEmpty() && !mySessionEntries.containsKey(contributor)) {
            listLines.add(line);
          }
        }
      }
      finally {
        reader.close();
      }
    }
    catch (IOException ignored) {
      // no previously generated resource exists: the very first build; same handling as in JMH's BenchmarkGenerator
    }
    for (List<String> entries : mySessionEntries.values()) {
      listLines.addAll(entries);
    }
    writeResource(LIST_RESOURCE, listLines);

    // MockHints models META-INF/CompilerHints: plain overwrite with the current session data, no merging
    TreeSet<String> hintLines = new TreeSet<String>();
    for (List<String> entries : mySessionEntries.values()) {
      for (String entry : entries) {
        hintLines.add("inline," + entry.substring(0, entry.indexOf('=')));
      }
    }
    writeResource(HINTS_RESOURCE, hintLines);
  }

  private void writeResource(String path, Iterable<String> lines) throws IOException {
    // no originating elements are passed, like in JMH's APGeneratorDestinaton.newResource()
    FileObject resource = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", path);
    Writer writer = new OutputStreamWriter(resource.openOutputStream(), StandardCharsets.UTF_8);
    try {
      for (String line : lines) {
        writer.write(line);
        writer.write('\n');
      }
    }
    finally {
      writer.close();
    }
  }
}
