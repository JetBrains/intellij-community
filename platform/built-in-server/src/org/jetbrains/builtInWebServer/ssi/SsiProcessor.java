/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.builtInWebServer.ssi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import io.netty.buffer.ByteBufUtf8Writer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class SsiProcessor {
  static final Logger LOG = Logger.getInstance(SsiProcessor.class);

  protected static final String COMMAND_START = "<!--#";
  protected static final String COMMAND_END = "-->";

  protected final Map<String, SsiCommand> commands = new THashMap<String, SsiCommand>();

  @SuppressWarnings("SpellCheckingInspection")
  public SsiProcessor(boolean allowExec) {
    commands.put("config", new SsiCommand() {
      @Override
      public long process(@NotNull SsiProcessingState state, @NotNull String commandName, @NotNull List<String> paramNames, @NotNull String[] paramValues, @NotNull ByteBufUtf8Writer writer) {
        for (int i = 0; i < paramNames.size(); i++) {
          String paramName = paramNames.get(i);
          String paramValue = paramValues[i];
          String substitutedValue = state.substituteVariables(paramValue);
          if (paramName.equalsIgnoreCase("errmsg")) {
            state.configErrorMessage = substitutedValue;
          }
          else if (paramName.equalsIgnoreCase("sizefmt")) {
            state.configSizeFmt = substitutedValue;
          }
          else if (paramName.equalsIgnoreCase("timefmt")) {
            state.setConfigTimeFormat(substitutedValue, false);
          }
          else {
            LOG.info("#config--Invalid attribute: " + paramName);
            // We need to fetch this value each time, since it may change during the loop
            writer.write(state.configErrorMessage);
          }
        }
        return 0;
      }
    });
    commands.put("echo", new SsiCommand() {
      @Override
      public long process(@NotNull SsiProcessingState state, @NotNull String commandName, @NotNull List<String> paramNames, @NotNull String[] paramValues, @NotNull ByteBufUtf8Writer writer) {
        String encoding = "entity";
        String originalValue = null;
        String errorMessage = state.configErrorMessage;
        for (int i = 0; i < paramNames.size(); i++) {
          String paramName = paramNames.get(i);
          String paramValue = paramValues[i];
          if (paramName.equalsIgnoreCase("var")) {
            originalValue = paramValue;
          }
          else if (paramName.equalsIgnoreCase("encoding")) {
            if (paramValue.equalsIgnoreCase("url") || paramValue.equalsIgnoreCase("entity") || paramValue.equalsIgnoreCase("none")) {
              encoding = paramValue;
            }
            else {
              LOG.info("#echo--Invalid encoding: " + paramValue);
              writer.write(errorMessage);
            }
          }
          else {
            LOG.info("#echo--Invalid attribute: " + paramName);
            writer.write(errorMessage);
          }
        }
        assert originalValue != null;
        String variableValue = state.getVariableValue(originalValue, encoding);
        writer.write(variableValue == null ? "(none)" : variableValue);
        return System.currentTimeMillis();
      }
    });
    //noinspection StatementWithEmptyBody
    if (allowExec) {
      // commands.put("exec", new SsiExec());
    }
    commands.put("include", new SsiCommand() {
      @Override
      public long process(@NotNull SsiProcessingState state, @NotNull String commandName, @NotNull List<String> paramNames, @NotNull String[] paramValues, @NotNull ByteBufUtf8Writer writer) {
        long lastModified = 0;
        String configErrorMessage = state.configErrorMessage;
        for (int i = 0; i < paramNames.size(); i++) {
          String paramName = paramNames.get(i);
          if (paramName.equalsIgnoreCase("file") || paramName.equalsIgnoreCase("virtual")) {
            String substitutedValue = state.substituteVariables(paramValues[i]);
            try {
              boolean virtual = paramName.equalsIgnoreCase("virtual");
              lastModified = state.ssiExternalResolver.getFileLastModified(substitutedValue, virtual);
              VirtualFile file = state.ssiExternalResolver.findFile(substitutedValue, virtual);
              if (file == null) {
                LOG.warn("#include-- Couldn't find file: " + substitutedValue);
                return 0;
              }

              InputStream in = file.getInputStream();
              try {
                writer.write(in, (int)file.getLength());
              }
              finally {
                in.close();
              }
            }
            catch (IOException e) {
              LOG.warn("#include--Couldn't include file: " + substitutedValue, e);
              writer.write(configErrorMessage);
            }
          }
          else {
            LOG.info("#include--Invalid attribute: " + paramName);
            writer.write(configErrorMessage);
          }
        }
        return lastModified;
      }
    });
    commands.put("flastmod", new SsiCommand() {
      @Override
      public long process(@NotNull SsiProcessingState state, @NotNull String commandName, @NotNull List<String> paramNames, @NotNull String[] paramValues, @NotNull ByteBufUtf8Writer writer) {
        long lastModified = 0;
        String configErrMsg = state.configErrorMessage;
        for (int i = 0; i < paramNames.size(); i++) {
          String paramName = paramNames.get(i);
          String paramValue = paramValues[i];
          String substitutedValue = state.substituteVariables(paramValue);
          if (paramName.equalsIgnoreCase("file") || paramName.equalsIgnoreCase("virtual")) {
            boolean virtual = paramName.equalsIgnoreCase("virtual");
            lastModified = state.ssiExternalResolver.getFileLastModified(substitutedValue, virtual);
            Strftime strftime = new Strftime(state.configTimeFmt, Locale.US);
            writer.write(strftime.format(new Date(lastModified)));
          }
          else {
            LOG.info("#flastmod--Invalid attribute: " + paramName);
            writer.write(configErrMsg);
          }
        }
        return lastModified;
      }
    });
    commands.put("fsize", new SsiFsize());
    commands.put("printenv", new SsiCommand() {
      @Override
      public long process(@NotNull SsiProcessingState state, @NotNull String commandName, @NotNull List<String> paramNames, @NotNull String[] paramValues, @NotNull ByteBufUtf8Writer writer) {
        long lastModified = 0;
        // any arguments should produce an error
        if (paramNames.isEmpty()) {
          Set<String> variableNames = new LinkedHashSet<String>();
          //These built-in variables are supplied by the mediator ( if not over-written by the user ) and always exist
          variableNames.add("DATE_GMT");
          variableNames.add("DATE_LOCAL");
          variableNames.add("LAST_MODIFIED");
          state.ssiExternalResolver.addVariableNames(variableNames);
          for (String variableName : variableNames) {
            String variableValue = state.getVariableValue(variableName);
            // This shouldn't happen, since all the variable names must have values
            if (variableValue == null) {
              variableValue = "(none)";
            }
            writer.write(variableName);
            writer.write('=');
            writer.write(variableValue);
            writer.write('\n');
            lastModified = System.currentTimeMillis();
          }
        }
        else {
          writer.write(state.configErrorMessage);
        }
        return lastModified;
      }
    });
    commands.put("set", new SsiCommand() {
      @Override
      public long process(@NotNull SsiProcessingState state, @NotNull String commandName, @NotNull List<String> paramNames, @NotNull String[] paramValues, @NotNull ByteBufUtf8Writer writer) {
        long lastModified = 0;
        String errorMessage = state.configErrorMessage;
        String variableName = null;
        for (int i = 0; i < paramNames.size(); i++) {
          String paramName = paramNames.get(i);
          String paramValue = paramValues[i];
          if (paramName.equalsIgnoreCase("var")) {
            variableName = paramValue;
          }
          else if (paramName.equalsIgnoreCase("value")) {
            if (variableName != null) {
              String substitutedValue = state.substituteVariables(paramValue);
              state.ssiExternalResolver.setVariableValue(variableName, substitutedValue);
              lastModified = System.currentTimeMillis();
            }
            else {
              LOG.info("#set--no variable specified");
              writer.write(errorMessage);
              throw new SsiStopProcessingException();
            }
          }
          else {
            LOG.info("#set--Invalid attribute: " + paramName);
            writer.write(errorMessage);
            throw new SsiStopProcessingException();
          }
        }
        return lastModified;
      }
    });

    SsiConditional ssiConditional = new SsiConditional();
    commands.put("if", ssiConditional);
    commands.put("elif", ssiConditional);
    commands.put("endif", ssiConditional);
    commands.put("else", ssiConditional);
  }

  /**
   * @return the most current modified date resulting from any SSI commands
   */
  public long process(@NotNull SsiExternalResolver ssiExternalResolver, @NotNull String fileContents, long lastModifiedDate, @NotNull ByteBufUtf8Writer writer) {
    SsiProcessingState ssiProcessingState = new SsiProcessingState(ssiExternalResolver, lastModifiedDate);
    int index = 0;
    boolean inside = false;
    StringBuilder command = new StringBuilder();
    try {
      while (index < fileContents.length()) {
        char c = fileContents.charAt(index);
        if (inside) {
          if (c == COMMAND_END.charAt(0) && charCmp(fileContents, index, COMMAND_END)) {
            inside = false;
            index += COMMAND_END.length();
            String commandName = parseCommand(command);
            if (LOG.isDebugEnabled()) {
              LOG.debug("SSIProcessor.process -- processing command: " + commandName);
            }
            List<String> paramNames = parseParamNames(command, commandName.length());
            String[] paramValues = parseParamValues(command, commandName.length(), paramNames.size());
            // We need to fetch this value each time, since it may change during the loop
            String configErrMsg = ssiProcessingState.configErrorMessage;
            SsiCommand ssiCommand = commands.get(commandName.toLowerCase(Locale.ENGLISH));
            String errorMessage = null;
            if (ssiCommand == null) {
              errorMessage = "Unknown command: " + commandName;
            }
            else if (paramValues == null) {
              errorMessage = "Error parsing directive parameters.";
            }
            else if (paramNames.size() != paramValues.length) {
              errorMessage = "Parameter names count does not match parameter values count on command: " + commandName;
            }
            else {
              // don't process the command if we are processing conditional commands only and the command is not conditional
              if (!ssiProcessingState.conditionalState.processConditionalCommandsOnly || ssiCommand instanceof SsiConditional) {
                long newLastModified = ssiCommand.process(ssiProcessingState, commandName, paramNames, paramValues, writer);
                if (newLastModified > lastModifiedDate) {
                  lastModifiedDate = newLastModified;
                }
              }
            }
            if (errorMessage != null) {
              LOG.warn(errorMessage);
              writer.write(configErrMsg);
            }
          }
          else {
            command.append(c);
            index++;
          }
        }
        else if (c == COMMAND_START.charAt(0) && charCmp(fileContents, index, COMMAND_START)) {
          inside = true;
          index += COMMAND_START.length();
          command.setLength(0);
        }
        else {
          if (!ssiProcessingState.conditionalState.processConditionalCommandsOnly) {
            writer.write(c);
          }
          index++;
        }
      }
    }
    catch (SsiStopProcessingException e) {
      //If we are here, then we have already stopped processing, so all is good
    }
    return lastModifiedDate;
  }

  @NotNull
  protected List<String> parseParamNames(@NotNull StringBuilder command, int start) {
    int bIdx = start;
    List<String> values = new SmartList<String>();
    boolean inside = false;
    StringBuilder builder = new StringBuilder();
    while (bIdx < command.length()) {
      if (inside) {
        while (bIdx < command.length() && command.charAt(bIdx) != '=') {
          builder.append(command.charAt(bIdx));
          bIdx++;
        }

        values.add(builder.toString());
        builder.setLength(0);
        inside = false;
        int quotes = 0;
        boolean escaped = false;
        for (; bIdx < command.length() && quotes != 2; bIdx++) {
          char c = command.charAt(bIdx);
          // Need to skip escaped characters
          if (c == '\\' && !escaped) {
            escaped = true;
            continue;
          }
          if (c == '"' && !escaped) {
            quotes++;
          }
          escaped = false;
        }
      }
      else {
        while (bIdx < command.length() && isSpace(command.charAt(bIdx))) {
          bIdx++;
        }
        if (bIdx >= command.length()) {
          break;
        }
        inside = true;
      }
    }
    return values;
  }

  @SuppressWarnings("AssignmentToForLoopParameter")
  protected String[] parseParamValues(@NotNull StringBuilder command, int start, int count) {
    int valueIndex = 0;
    boolean inside = false;
    String[] values = new String[count];
    StringBuilder builder = new StringBuilder();
    char endQuote = 0;
    for (int bIdx = start; bIdx < command.length(); bIdx++) {
      if (!inside) {
        while (bIdx < command.length() && !isQuote(command.charAt(bIdx))) {
          bIdx++;
        }
        if (bIdx >= command.length()) {
          break;
        }
        inside = true;
        endQuote = command.charAt(bIdx);
      }
      else {
        boolean escaped = false;
        for (; bIdx < command.length(); bIdx++) {
          char c = command.charAt(bIdx);
          // Check for escapes
          if (c == '\\' && !escaped) {
            escaped = true;
            continue;
          }
          // If we reach the other " then stop
          if (c == endQuote && !escaped) {
            break;
          }
          // Since parsing of attributes and var
          // substitution is done in separate places,
          // we need to leave escape in the string
          if (c == '$' && escaped) {
            builder.append('\\');
          }
          escaped = false;
          builder.append(c);
        }
        // If we hit the end without seeing a quote
        // the signal an error
        if (bIdx == command.length()) {
          return null;
        }
        values[valueIndex++] = builder.toString();
        builder.setLength(0);
        inside = false;
      }
    }
    return values;
  }

  @NotNull
  private String parseCommand(@NotNull StringBuilder instruction) {
    int firstLetter = -1;
    int lastLetter = -1;
    for (int i = 0; i < instruction.length(); i++) {
      char c = instruction.charAt(i);
      if (Character.isLetter(c)) {
        if (firstLetter == -1) {
          firstLetter = i;
        }
        lastLetter = i;
      }
      else if (isSpace(c)) {
        if (lastLetter > -1) {
          break;
        }
      }
      else {
        break;
      }
    }
    return firstLetter == -1 ? "" : instruction.substring(firstLetter, lastLetter + 1);
  }

  protected boolean charCmp(String buf, int index, String command) {
    return buf.regionMatches(index, command, 0, command.length());
  }


  protected boolean isSpace(char c) {
    return c == ' ' || c == '\n' || c == '\t' || c == '\r';
  }

  protected boolean isQuote(char c) {
    return c == '\'' || c == '\"' || c == '`';
  }
}