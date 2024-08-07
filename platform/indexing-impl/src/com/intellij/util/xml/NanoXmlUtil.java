// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.Stack;
import com.intellij.util.text.CharSequenceReader;
import net.n3.nanoxml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Properties;

public final class NanoXmlUtil {
  private static final Logger LOG = Logger.getInstance(NanoXmlUtil.class);

  private NanoXmlUtil() {
  }

  private static MyXMLReader createReader(@NotNull PsiFile psiFile) {
    return new MyXMLReader(new CharSequenceReader(psiFile.getViewProvider().getContents()));
  }

  public static void parse(@NotNull InputStream is, @NotNull IXMLBuilder builder) {
    try (is) {
      parse(new MyXMLReader(is), builder);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static void parse(@NotNull Reader reader, @NotNull IXMLBuilder builder) {
    parse(reader, builder, null);
  }

  public static void parse(@NotNull Reader reader, @NotNull IXMLBuilder builder, @Nullable IXMLValidator validator) {
    try (reader) {
      parse(new MyXMLReader(reader), builder, validator);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public static void parse(StdXMLReader r, @NotNull IXMLBuilder builder) {
    parse(r, builder, null);
  }

  public static void parse(StdXMLReader r, @NotNull IXMLBuilder builder, @Nullable IXMLValidator validator) {
    StdXMLParser parser = new StdXMLParser(
      r,
      builder,
      validator == null ? new EmptyValidator() : validator,
      new EmptyEntityResolver()
    );
    try {
      parser.parse();
    }
    catch (ParserStoppedXmlException ignore) {
    }
    catch (XMLException e) {
      if (e.getException() instanceof ProcessCanceledException) {
        throw new ProcessCanceledException(e);
      }
      LOG.debug(e);
    }
  }

  public static @NotNull XmlFileHeader parseHeader(VirtualFile file) {
    try {
      return parseHeaderWithException(file);
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static @NotNull XmlFileHeader parseHeaderWithException(Reader reader) {
    return parseHeader(new MyXMLReader(reader));
  }

  public static @NotNull XmlFileHeader parseHeaderWithException(final VirtualFile file) throws IOException {
    try (InputStream stream = file.getInputStream()) {
      return parseHeader(new MyXMLReader(stream));
    }
  }

  public static @NotNull XmlFileHeader parseHeader(final Reader reader) {
    return parseHeader(new MyXMLReader(reader));
  }

  public static @NotNull XmlFileHeader parseHeader(PsiFile file) {
    return parseHeader(createReader(file));
  }

  private static @NotNull XmlFileHeader parseHeader(final MyXMLReader r) {
    final RootTagInfoBuilder builder = new RootTagInfoBuilder();
    parse(r, builder);
    return new XmlFileHeader(builder.getRootTagName(), builder.getNamespace(), r.publicId, r.systemId);
  }

  public static String createLocation(@NonNls String ...tagNames) {
    StringBuilder result = new StringBuilder();
    for (String tagName : tagNames) {
      result.append(".");
      result.append(tagName);
    }

    return result.toString();
  }

  /**
   * @deprecated left for API compatibility
   */
  @Deprecated(forRemoval = true)
  public abstract static class IXMLBuilderAdapter implements NanoXmlBuilder {
  }

  public static class BaseXmlBuilder implements NanoXmlBuilder {
    private final Stack<String> myLocation = new Stack<>();

    @Override
    public void startBuilding(String systemID, int lineNr) {
      myLocation.push("");
    }

    @Override
    public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
      myLocation.push(myLocation.peek() + "." + name);
    }

    @Override
    public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
      myLocation.pop();
    }

    protected static String readText(final Reader reader) throws IOException {
      return new String(StreamUtil.readTextAndConvertSeparators(reader));
    }

    protected @NonNls String getLocation() {
      return myLocation.peek();
    }
  }

  public static class EmptyValidator extends NonValidator {
    @Override
    public void elementStarted(String name, String systemId, int lineNr) {
    }

    @Override
    public void attributeAdded(String key, String value, String systemId, int lineNr) {
    }

    @Override
    public void elementAttributesProcessed(String name, Properties extraAttributes, String systemId, int lineNr) {
    }
  }

  private static final class EmptyEntityResolver implements IXMLEntityResolver {
    @Override
    public void addInternalEntity(String name, String value) {
    }

    @Override
    public void addExternalEntity(String name, String publicID, String systemID) {
    }

    @Override
    public Reader getEntity(StdXMLReader xmlReader, String name) {
      return new StringReader("");
    }

    @Override
    public boolean isExternalEntity(String name) {
      return false;
    }
  }

  private static final class MyXMLReader extends StdXMLReader {
    private String publicId;
    private String systemId;

    MyXMLReader(@NotNull Reader documentReader) {
      super(documentReader);
    }

    MyXMLReader(InputStream stream) throws IOException {
      super(stream);
    }

    @Override
    public Reader openStream(String publicId, String systemId) {
      this.publicId = Strings.isEmpty(publicId) ? null : publicId;
      this.systemId = Strings.isEmpty(systemId) ? null : systemId;
      return new StringReader(" ");
    }
  }

  public static final class ParserStoppedXmlException extends XMLException {
    public static final ParserStoppedXmlException INSTANCE = new ParserStoppedXmlException();

    private ParserStoppedXmlException() {
      super("Parsing stopped");
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

  private static final class RootTagInfoBuilder implements IXMLBuilder {
    private String myRootTagName;
    private String myNamespace;

    @Override
    public void startBuilding(final String systemID, final int lineNr) {
    }

    @Override
    public void newProcessingInstruction(final String target, final Reader reader) {
    }

    @Override
    public void startElement(final String name, final String nsPrefix, final String nsURI, final String systemID, final int lineNr) throws Exception {
      myRootTagName = name;
      myNamespace = nsURI;
      throw ParserStoppedXmlException.INSTANCE;
    }

    @Override
    public void addAttribute(final String key, final String nsPrefix, final String nsURI, final String value, final String type) {
    }

    @Override
    public void elementAttributesProcessed(final String name, final String nsPrefix, final String nsURI) {
    }

    @Override
    public void endElement(final String name, final String nsPrefix, final String nsURI) {
    }

    @Override
    public void addPCData(final Reader reader, final String systemID, final int lineNr) {
    }

    public String getNamespace() {
      return myNamespace;
    }

    public String getRootTagName() {
      return myRootTagName;
    }

    @Override
    public String getResult() {
      return myRootTagName;
    }
  }
}
