package org.jetbrains.builtInWebServer.ssi;

import io.netty.buffer.ByteBufUtf8Writer;

import java.util.List;

public interface SsiCommand {
  long process(SsiProcessingState state, String commandName, List<String> paramNames, String[]paramValues, ByteBufUtf8Writer writer);
}
