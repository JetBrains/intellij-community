package com.intellij.execution.jshell.frontend;

import com.intellij.execution.jshell.protocol.*;
import jdk.jshell.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;


/**
 * @author Eugene Zhuravlev
 * Date: 12-Jun-17
 *
 * @noinspection UseOfSystemOutOrSystemErr
 */
public class Main {
  private static final String ARG_CLASSPATH = "--class-path";
  private static final String ARG_CLASSPATH_FILE = "--@class-path";
  private static final Consumer<String> NULL_CONSUMER = s -> {};

  //private static Request createTestRequest() {
  //  return new Request(UUID.randomUUID().toString(), Request.Command.EVAL, "int a = 77;\n" +
  //                                                                         "int b = a + 3");
  //}

  public static void main(String[] args) throws Exception {
    final MessageReader<Request> reader = new MessageReader<>(new BufferedInputStream(System.in), Request.class);
    final MessageWriter<Response> writer = new MessageWriter<>(new BufferedOutputStream(System.out), Response.class);

    try (JShell shell = JShell.create()) {
      configureJShell(args, shell);
      while (true) {
        final Request request = reader.receive(NULL_CONSUMER);
        if (request == null) {
          break;
        }
        final Request.Command command = request.getCommand();
        if (command == Request.Command.EXIT) {
          return;
        }

        final Response response = new Response();
        response.setUid(request.getUid());

        try {
          // first, handle eval classpath if any
          final List<String> cp = request.getClassPath();
          if (cp != null && !cp.isEmpty()) {
            for (String path : cp) {
              shell.addToClasspath(path);
            }
          }
          
          if (command == Request.Command.DROP_STATE) {
            shell.snippets().forEach(snippet -> exportEvents(shell, shell.drop(snippet), response));
          }
          else if (command == Request.Command.EVAL) {
            final List<String> toEval = new ArrayList<>();

            String input = request.getCodeText();
            do {
              final SourceCodeAnalysis.CompletionInfo info = shell.sourceCodeAnalysis().analyzeCompletion(input);
              final SourceCodeAnalysis.Completeness completeness = info.completeness();
              if (completeness.isComplete()) {
                toEval.add(completeness == SourceCodeAnalysis.Completeness.COMPLETE_WITH_SEMI ? info.source() + ";" : info.source());
              }
              else if (completeness != SourceCodeAnalysis.Completeness.EMPTY){
                // we try to evaluate even the snippets containing errors so that those errors will be displayed
                toEval.add(info.source());
              }
              input = info.remaining();
            }
            while (input != null && !input.isEmpty());

            for (String inputElement : toEval) {
              exportEvents(shell, shell.eval(inputElement), response);
            }
          }
        }
        finally {
          writer.send(response);
        }
      }
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
    finally {
      System.out.println("\nJShell terminated.");
    }
  }

  private static void exportEvents(JShell shell, List<SnippetEvent> events, Response response) {
    for (SnippetEvent event : events) {
      final Snippet.Status status = event.status();
      final Snippet snippet = event.snippet();
      final Event e = new Event(
        createCodeSnippet(snippet),
        createCodeSnippet(event.causeSnippet()),
        convertEnum(status, CodeSnippet.Status.class),
        convertEnum(event.previousStatus(), CodeSnippet.Status.class),
        event.value()
      );
      //noinspection ThrowableNotThrown
      final JShellException exception = event.exception();
      if (exception != null) {
        e.setExceptionText(exception.getMessage());
      }
      if (status == Snippet.Status.RECOVERABLE_DEFINED || status == Snippet.Status.RECOVERABLE_NOT_DEFINED || status == Snippet.Status.REJECTED) {
        final StringBuilder buf = new StringBuilder();
        shell.diagnostics(snippet).forEach(diagnostic -> {
          final String message = diagnostic.getMessage(Locale.US);
          if (message != null && !message.isEmpty()) {
            if (buf.length() > 0){
              buf.append("\n");
            }
            if (diagnostic.isError()) {
              buf.append("ERROR: ");
            }
            buf.append(message);
          }
        });
        if (buf.length() > 0) {
          e.setDiagnostic(buf.toString());
        }
      }
      response.addEvent(e);
    }
  }

  private static void configureJShell(String[] args, JShell shell) throws IOException {
    // todo: add more parameters if needed
    boolean cpFound = false;
    boolean cpFileFound = false;
    for (String arg : args) {
      if (ARG_CLASSPATH.equals(arg)) {
        cpFound = true;
      }
      else if (ARG_CLASSPATH_FILE.equals(arg)) {
        cpFileFound = true;
      }
      else {
        if (cpFound) {
          cpFound = false;
          for (String path : arg.split(File.pathSeparator)) {
            shell.addToClasspath(path);
          }
        }
        else if (cpFileFound) {
          cpFileFound = false;
          final File cpFile = new File(arg);
          try (final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(cpFile), StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
              shell.addToClasspath(line);
            }
          }
        }
      }
    }
  }

  private static CodeSnippet createCodeSnippet(Snippet snippet) {
    return snippet == null ? null : new CodeSnippet(
      snippet.id(),
      convertEnum(snippet.kind(), CodeSnippet.Kind.class),
      convertEnum(snippet.subKind(), CodeSnippet.SubKind.class),
      snippet.source(),
      createPresentation(snippet)
    );
  }

  private static String createPresentation(Snippet snippet) {
    if (snippet instanceof ExpressionSnippet) {
      final ExpressionSnippet expr = (ExpressionSnippet)snippet;
      return expr.typeName() + " " + expr.name();
    }
    if (snippet instanceof ImportSnippet) {
      return ((ImportSnippet)snippet).fullname();
    }
    if (snippet instanceof MethodSnippet) {
      final MethodSnippet methodSnippet = (MethodSnippet)snippet;
      final StringBuilder buf = new StringBuilder(getReturnType(methodSnippet));
      if (buf.length() > 0) {
        buf.append(" ");
      }
      return buf.append(methodSnippet.name()).append("(").append(methodSnippet.parameterTypes()).append(")").toString();
    }
    if (snippet instanceof TypeDeclSnippet) {
      return ((TypeDeclSnippet)snippet).name();
    }
    if (snippet instanceof VarSnippet) {
      final VarSnippet varSnippet = (VarSnippet)snippet;
      return varSnippet.typeName() + " " + varSnippet.name();
    }
    if (snippet instanceof PersistentSnippet) {
      return ((PersistentSnippet)snippet).name();
    }
    return null;
  }

  private static String getReturnType(MethodSnippet methodSnippet) {
    final String sig = methodSnippet.signature();
    final int idx = sig == null? -1 : sig.lastIndexOf(")");
    return idx > 0? sig.substring(idx + 1) : "";
  }

  private static <TI extends Enum<TI>, TO extends Enum<TO>> TO convertEnum(Enum<TI> from, Class<TO> toEnumOfClass) {
    if (from != null) {
      try {
        return Enum.valueOf(toEnumOfClass, from.name());
      }
      catch (IllegalArgumentException ignored) {
      }
    }
    return Enum.valueOf(toEnumOfClass, "UNKNOWN");
  }
}
