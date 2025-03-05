package com.intellij.microservices.mime;

import org.jetbrains.annotations.NonNls;

import java.util.Set;
import java.util.regex.Pattern;

public final @NonNls class MimeTypes {
  private MimeTypes() {
  }

  public static final Pattern MIME_PATTERN = Pattern.compile("[^\\p{Cc}^\\s]+/[^\\p{Cc}^\\s]+");

  // See also https://en.wikipedia.org/wiki/Media_type

  public static final String APPLICATION_XML = "application/xml";
  public static final String APPLICATION_JSON = "application/json";
  public static final String APPLICATION_JAVASCRIPT= "application/javascript";
  public static final String APPLICATION_X_YAML = "application/x-yaml";
  public static final String APPLICATION_YAML = "application/yaml";
  public static final String APPLICATION_GRAPHQL = "application/graphql";

  public static final String APPLICATION_ATOM_XML = "application/atom+xml";
  public static final String APPLICATION_XHTML_XML = "application/xhtml+xml";
  public static final String APPLICATION_SVG_XML = "application/svg+xml";
  public static final String APPLICATION_SQL = "application/sql";
  public static final String APPLICATION_PDF = "application/pdf";
  public static final String APPLICATION_ZIP = "application/zip";

  public static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";
  public static final String MULTIPART_FORM_DATA = "multipart/form-data";
  public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

  public static final String TEXT_PLAIN = "text/plain";
  public static final String TEXT_XML = "text/xml";
  public static final String TEXT_HTML = "text/html";
  public static final String TEXT_JSON = "text/json";
  public static final String TEXT_CSV = "text/csv";

  public static final String TEXT_CSS = "text/css";

  public static final String IMAGE_PNG = "image/png";
  public static final String IMAGE_JPEG = "image/jpeg";
  public static final String IMAGE_GIF = "image/gif";
  public static final String IMAGE_WEBP = "image/webp";

  public static final String IMAGE_SVG = "image/svg+xml";

  public static final String AUDIO_MPEG = "audio/mpeg";
  public static final String AUDIO_VORBIS = "audio/vorbis";

  // Streaming
  public static final String TEXT_EVENT_STREAM = "text/event-stream";
  public static final String APPLICATION_STREAM_JSON = "application/stream+json";
  public static final String APPLICATION_X_NDJSON = "application/x-ndjson";

  // Most common MIME Types for web development
  public static final String[] PREDEFINED_MIME_VARIANTS = {
    APPLICATION_JSON, APPLICATION_XML, APPLICATION_X_YAML, APPLICATION_GRAPHQL,
    APPLICATION_ATOM_XML, APPLICATION_XHTML_XML, APPLICATION_SVG_XML,
    APPLICATION_SQL, APPLICATION_PDF, APPLICATION_ZIP,
    APPLICATION_FORM_URLENCODED, MULTIPART_FORM_DATA, APPLICATION_OCTET_STREAM,
    TEXT_PLAIN, TEXT_XML, TEXT_HTML, TEXT_JSON, TEXT_CSV,
    IMAGE_PNG, IMAGE_JPEG, IMAGE_GIF, IMAGE_WEBP, IMAGE_SVG,
    AUDIO_MPEG, AUDIO_VORBIS,
    TEXT_EVENT_STREAM, APPLICATION_STREAM_JSON, APPLICATION_X_NDJSON
  };

  public static final Set<String> PREDEFINED_TEXT_MIME_TYPES = Set.of(
    APPLICATION_XML, APPLICATION_X_YAML, APPLICATION_YAML, APPLICATION_JSON, APPLICATION_GRAPHQL, APPLICATION_ATOM_XML, APPLICATION_XHTML_XML,
    APPLICATION_SVG_XML, APPLICATION_SQL, APPLICATION_FORM_URLENCODED,
    TEXT_PLAIN, TEXT_XML, TEXT_HTML, TEXT_JSON, TEXT_CSV,
    IMAGE_SVG
  );
}
