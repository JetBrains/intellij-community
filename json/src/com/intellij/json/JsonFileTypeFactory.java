package com.intellij.json;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 * @deprecated use &lt;fileType&gt; extension point instead
 */
@Deprecated
public class JsonFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(JsonFileType.INSTANCE, JsonFileType.DEFAULT_EXTENSION);
  }
}
