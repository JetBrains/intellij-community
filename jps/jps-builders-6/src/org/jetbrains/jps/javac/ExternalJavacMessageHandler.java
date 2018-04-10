/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.BinaryContent;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.Locale;

/**
 * @author Eugene Zhuravlev
 */
public class ExternalJavacMessageHandler {
  private final DiagnosticOutputConsumer myDiagnosticSink;
  private final OutputFileConsumer myOutputSink;
  @Nullable
  private final String myEncodingName;
  private volatile boolean myTerminatedSuccessfully;

  public ExternalJavacMessageHandler(DiagnosticOutputConsumer diagnosticSink,
                                     OutputFileConsumer outputSink,
                                     @Nullable final String encodingName) {
    myDiagnosticSink = diagnosticSink;
    myOutputSink = outputSink;
    myEncodingName = encodingName;
  }

  public boolean handleMessage(MessageLite message) {
    try {
      final JavacRemoteProto.Message msg = (JavacRemoteProto.Message)message;
      final JavacRemoteProto.Message.Type messageType = msg.getMessageType();

      if (messageType == JavacRemoteProto.Message.Type.RESPONSE) {
        final JavacRemoteProto.Message.Response response = msg.getResponse();
        final JavacRemoteProto.Message.Response.Type responseType = response.getResponseType();
  
        if (responseType == JavacRemoteProto.Message.Response.Type.BUILD_MESSAGE) {
          final JavacRemoteProto.Message.Response.CompileMessage compileMessage = response.getCompileMessage();
          final JavacRemoteProto.Message.Response.CompileMessage.Kind messageKind = compileMessage.getKind();
  
          if (messageKind == JavacRemoteProto.Message.Response.CompileMessage.Kind.STD_OUT) {
            if (compileMessage.hasText()) {
              myDiagnosticSink.outputLineAvailable(compileMessage.getText());
            }
          }
          else {
            final String sourceUri = compileMessage.hasSourceUri()? compileMessage.getSourceUri() : null;
            final JavaFileObject srcFileObject = sourceUri != null? new DummyJavaFileObject(URI.create(sourceUri)) : null;
            myDiagnosticSink.report(new DummyDiagnostic(convertKind(messageKind), srcFileObject, compileMessage));
          }
  
          return false;
        }
  
        if (responseType == JavacRemoteProto.Message.Response.Type.OUTPUT_OBJECT) {
          final JavacRemoteProto.Message.Response.OutputObject outputObject = response.getOutputObject();
          final JavacRemoteProto.Message.Response.OutputObject.Kind kind = outputObject.getKind();
  
          final String outputRoot = outputObject.hasOutputRoot()? outputObject.getOutputRoot() : null;
          final File outputRootFile = outputRoot != null? new File(outputRoot) : null;
  
          final BinaryContent fileObjectContent;
          final ByteString content = outputObject.hasContent()? outputObject.getContent() : null;
          if (content != null) {
            final byte[] bytes = content.toByteArray();
            fileObjectContent = new BinaryContent(bytes, 0, bytes.length);
          }
          else {
            fileObjectContent = null;
          }
  
          final String sourceUri = outputObject.hasSourceUri()? outputObject.getSourceUri() : null;
          final URI srcUri = sourceUri != null? URI.create(sourceUri) : null;
          final OutputFileObject fileObject = new OutputFileObject(
            null,
            outputRootFile,
            outputObject.hasRelativePath()? outputObject.getRelativePath() : null,
            new File(outputObject.getFilePath()),
            convertKind(kind),
            outputObject.hasClassName()? outputObject.getClassName() : null,
            srcUri,
            myEncodingName, fileObjectContent
          );
  
          myOutputSink.save(fileObject);
          return false;
        }
        
        if (responseType == JavacRemoteProto.Message.Response.Type.SRC_FILE_LOADED) {
          final JavacRemoteProto.Message.Response.OutputObject outputObject = response.getOutputObject();
          final File file = new File(outputObject.getFilePath());
          myDiagnosticSink.javaFileLoaded(file);
          return false;
        }
  
        if (responseType == JavacRemoteProto.Message.Response.Type.CLASS_DATA) {
          final JavacRemoteProto.Message.Response.ClassData data = response.getClassData();
          final String className = data.getClassName();
          final Collection<String> imports = data.getImportStatementList();
          final Collection<String> staticImports = data.getStaticImportList();
          myDiagnosticSink.registerImports(className, imports, staticImports);
          return false;
        }
  
        if (responseType == JavacRemoteProto.Message.Response.Type.CUSTOM_OUTPUT_OBJECT) {
          final JavacRemoteProto.Message.Response.OutputObject outputObject = response.getOutputObject();
          final String pluginId = outputObject.getFilePath();
          final String name = outputObject.getClassName();
          final byte[] content = outputObject.hasContent()? outputObject.getContent().toByteArray() : new byte[0];
          myDiagnosticSink.customOutputData(pluginId, name, content);
          return false;
        }

        if (responseType == JavacRemoteProto.Message.Response.Type.BUILD_COMPLETED) {
          if (response.hasCompletionStatus()) {
            myTerminatedSuccessfully = response.getCompletionStatus();
          }
          return true;
        }
  
        throw new Exception("Unsupported response type: " + responseType.name());
      }

      if (messageType == JavacRemoteProto.Message.Type.FAILURE) {
        final JavacRemoteProto.Message.Failure failure = msg.getFailure();
        final StringBuilder buf = new StringBuilder();
        if (failure.hasDescription()) {
          buf.append(failure.getDescription());
        }
        if (failure.hasStacktrace()) {
          if (buf.length() > 0) {
            buf.append("\n");
          }
          buf.append(failure.getStacktrace());
        }
        myDiagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, buf.toString()));
        return true;
      }

      throw new Exception("Unsupported message type: " + messageType.name());
    }
    catch (Throwable e) {
      myDiagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, e.getMessage()));
      return true;
    }
  }

  public boolean isTerminatedSuccessfully() {
    return myTerminatedSuccessfully;
  }

  private static Diagnostic.Kind convertKind(JavacRemoteProto.Message.Response.CompileMessage.Kind kind) {
    switch (kind) {
      case ERROR: return Diagnostic.Kind.ERROR;
      case WARNING: return Diagnostic.Kind.WARNING;
      case MANDATORY_WARNING: return Diagnostic.Kind.MANDATORY_WARNING;
      case NOTE: return Diagnostic.Kind.NOTE;
      default : return Diagnostic.Kind.OTHER;
    }
  }

  private static OutputFileObject.Kind convertKind(JavacRemoteProto.Message.Response.OutputObject.Kind kind) {
    switch (kind) {
      case CLASS: return JavaFileObject.Kind.CLASS;
      case HTML: return JavaFileObject.Kind.HTML;
      case SOURCE: return JavaFileObject.Kind.SOURCE;
      default : return JavaFileObject.Kind.OTHER;
    }
  }

  private static class DummyDiagnostic implements Diagnostic<JavaFileObject> {

    private final Kind myMessageKind;
    private final JavaFileObject mySrcFileObject;
    private final JavacRemoteProto.Message.Response.CompileMessage myCompileMessage;

    public DummyDiagnostic(final Kind messageKind, JavaFileObject srcFileObject, JavacRemoteProto.Message.Response.CompileMessage compileMessage) {
      myMessageKind = messageKind;
      mySrcFileObject = srcFileObject;
      myCompileMessage = compileMessage;
    }

    public Kind getKind() {
      return myMessageKind;
    }

    public JavaFileObject getSource() {
      return mySrcFileObject;
    }

    public long getPosition() {
      return myCompileMessage.hasProblemLocationOffset()? myCompileMessage.getProblemLocationOffset() : -1;
    }

    public long getStartPosition() {
      return myCompileMessage.hasProblemBeginOffset()? myCompileMessage.getProblemBeginOffset() : -1;
    }

    public long getEndPosition() {
      return myCompileMessage.hasProblemEndOffset()? myCompileMessage.getProblemEndOffset() : -1;
    }

    public long getLineNumber() {
      return myCompileMessage.hasLine()? myCompileMessage.getLine() : -1;
    }

    public long getColumnNumber() {
      return myCompileMessage.hasColumn()? myCompileMessage.getColumn() : -1;
    }

    public String getCode() {
      return null;
    }

    public String getMessage(Locale locale) {
      return myCompileMessage.hasText()? myCompileMessage.getText() : null;
    }
  }
}
