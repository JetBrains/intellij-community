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
 * A second JMH-style processor used to verify per-processor attribution of the generated resources:
 * processes {@link Mark}-annotated methods, generates a companion source per contributing class
 * (with the originating element) and aggregates META-INF/MarkList (read-merged, no originating elements).
 */
@SupportedAnnotationTypes("mockapt.Mark")
public class MockMarkProcessor extends AbstractProcessor {
  private static final String LIST_RESOURCE = "META-INF/MarkList";

  private final Map<String, List<String>> mySessionEntries = new TreeMap<String, List<String>>();

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      if (roundEnv.processingOver()) {
        writeResource();
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
    for (Element element : roundEnv.getRootElements()) {
      if (element.getKind() != ElementKind.CLASS || !(element instanceof TypeElement)) {
        continue;
      }
      TypeElement type = (TypeElement)element;
      String classFqn = type.getQualifiedName().toString();
      if (mySessionEntries.containsKey(classFqn)) {
        continue;
      }
      List<ExecutableElement> markedMethods = new ArrayList<ExecutableElement>();
      for (Element member : type.getEnclosedElements()) {
        if (member.getKind() == ElementKind.METHOD && member.getAnnotation(Mark.class) != null) {
          markedMethods.add((ExecutableElement)member);
        }
      }
      if (markedMethods.isEmpty()) {
        continue;
      }
      int lastDot = classFqn.lastIndexOf('.');
      String pkg = classFqn.substring(0, lastDot);
      String simpleName = type.getSimpleName().toString();
      List<String> entries = new ArrayList<String>();
      StringBuilder body = new StringBuilder();
      body.append("package ").append(pkg).append(".gen;\n\n");
      body.append("public final class ").append(simpleName).append("MarkGen {\n");
      for (ExecutableElement method : markedMethods) {
        String methodName = method.getSimpleName().toString();
        entries.add(classFqn + "#" + methodName);
        body.append("  public static String call_").append(methodName).append("(").append(classFqn).append(" instance) {\n");
        body.append("    return String.valueOf(instance.").append(methodName).append("());\n");
        body.append("  }\n");
      }
      body.append("}\n");
      mySessionEntries.put(classFqn, entries);
      FileObject genSource = processingEnv.getFiler().createSourceFile(pkg + ".gen." + simpleName + "MarkGen", type);
      Writer writer = genSource.openWriter();
      try {
        writer.write(body.toString());
      }
      finally {
        writer.close();
      }
    }
  }

  private void writeResource() throws IOException {
    TreeSet<String> lines = new TreeSet<String>();
    try {
      FileObject existing = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", LIST_RESOURCE);
      BufferedReader reader = new BufferedReader(new InputStreamReader(existing.openInputStream(), StandardCharsets.UTF_8));
      try {
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
          int hash = line.indexOf('#');
          String contributor = hash > 0? line.substring(0, hash) : "";
          if (!line.isEmpty() && !mySessionEntries.containsKey(contributor)) {
            lines.add(line);
          }
        }
      }
      finally {
        reader.close();
      }
    }
    catch (IOException ignored) {
      // first build: no previous content
    }
    for (List<String> entries : mySessionEntries.values()) {
      lines.addAll(entries);
    }
    FileObject resource = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", LIST_RESOURCE);
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
