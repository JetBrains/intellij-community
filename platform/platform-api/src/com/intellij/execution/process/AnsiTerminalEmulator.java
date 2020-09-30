// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

import static com.intellij.execution.process.AnsiCommands.*;
import static com.intellij.execution.process.AnsiStreamingLexer.SGR_SUFFIX;
import static com.intellij.execution.process.AnsiTerminalEmulator.BlinkSpeed.RAPID_BLINK;
import static com.intellij.execution.process.AnsiTerminalEmulator.BlinkSpeed.SLOW_BLINK;
import static com.intellij.execution.process.AnsiTerminalEmulator.FrameType.*;
import static com.intellij.execution.process.AnsiTerminalEmulator.Underline.DOUBLE_UNDERLINE;
import static com.intellij.execution.process.AnsiTerminalEmulator.Underline.SINGLE_UNDERLINE;
import static com.intellij.execution.process.AnsiTerminalEmulator.Weight.BOLD;
import static com.intellij.execution.process.AnsiTerminalEmulator.Weight.FAINT;
import static com.intellij.openapi.editor.markup.EffectType.BOLD_LINE_UNDERSCORE;
import static com.intellij.openapi.editor.markup.EffectType.LINE_UNDERSCORE;

/**
 * Emulates the ANSI terminal state
 */
class AnsiTerminalEmulator {
  private static final Logger LOG = Logger.getInstance(AnsiTerminalEmulator.class);

  private static final AnsiTerminalEmulator EMPTY_EMULATOR = new AnsiTerminalEmulator();
  private static final int MAX_8BIT_COLOR_INDEX = 15;
  private static final int COLOR_CUBE_MIN_INDEX = MAX_8BIT_COLOR_INDEX + 1;
  private static final int GRAY_MIN_INDEX = 232;

  @NotNull
  private TerminalFont myFont = TerminalFont.DEFAULT;
  @NotNull
  private Weight myWeight = Weight.DEFAULT;
  @NotNull
  private Underline myUnderline = Underline.DEFAULT;
  @NotNull
  private BlinkSpeed myBlink = BlinkSpeed.DEFAULT;
  @NotNull
  private FrameType myFrameType = FrameType.DEFAULT;

  private boolean myIsItalic;
  private boolean myIsInverse;
  private boolean myIsConseal;
  private boolean myIsCrossedOut;
  private boolean myIsFraktur;
  private boolean myIsOverlined;

  private AnsiTerminalColor myBackgroundColor;
  private AnsiTerminalColor myForegroundColor;

  private final ClearableLazyValue<String> mySerializedSgrStateProvider = ClearableLazyValue.create(
    () -> computeAnsiSerializedSGRState());

  /**
   * Consumes <a href="https://en.wikipedia.org/wiki/ANSI_escape_code#SGR_(Select_Graphic_Rendition)_parameters">SGR - Select Graphic Rendition</a> control sequence and changes emulator's state accordingly
   */
  public void processSgr(@NotNull String sgrSequenceBody) {
    /*
      The ITU's T.416 Information technology - Open Document Architecture (ODA) and interchange format:
      https://www.itu.int/rec/dologin_pub.asp?lang=e&id=T-REC-T.416-199303-I!!PDF-E&type=items
      uses ':' as separator characters instead       *
     */
    String separator = StringUtil.containsChar(sgrSequenceBody, ':') ? ":" : ";";
    List<String> sgrSequenceParts = StringUtil.split(sgrSequenceBody, separator, true, false);
    Iterator<Integer> sgrCodesIterator = ContainerUtil.map(sgrSequenceParts, it -> {
      try {
        return it.isEmpty() ? 0 : Integer.parseInt(it);
      }
      catch (NumberFormatException e) {
        LOG.warn("Could not parse integer: " + it + " in " + sgrSequenceBody);
        return null;
      }
    }).iterator();

    while (sgrCodesIterator.hasNext()) {
      Integer command = sgrCodesIterator.next();
      if (command == null) {
        break;
      }
      processSgr(command, sgrCodesIterator);
    }
  }

  /**
   * Changes terminal state according to {@code commandCode}
   * @param sgrCodesIterator iterator of subsequent codes if any. May be necessary for multi-param sgr sequences, like setting color
   */
  private void processSgr(int commandCode, Iterator<Integer> sgrCodesIterator) {
    switch (commandCode) {
      case SGR_COMMAND_RESET:
        resetTerminal();
        break;
      case SGR_COMMAND_BOLD:
        myWeight = BOLD;
        break;
      case SGR_COMMAND_FAINT:
        myWeight = FAINT;
        break;
      case SGR_COMMAND_ITALIC:
        myIsItalic = true;
        break;
      case SGR_COMMAND_UNDERLINE:
        myUnderline = SINGLE_UNDERLINE;
        break;
      case SGR_COMMAND_BLINK_SLOW:
        myBlink = SLOW_BLINK;
        break;
      case SGR_COMMAND_BLINK_RAPID:
        myBlink = RAPID_BLINK;
        break;
      case SGR_COMMAND_INVERSE:
        myIsInverse = true;
        break;
      case SGR_COMMAND_CONCEAL:
        myIsConseal = true;
        break;
      case SGR_COMMAND_CROSS_OUT:
        myIsCrossedOut = true;
        break;
      case SGR_COMMAND_PRIMARY_FONT:
        myFont = TerminalFont.DEFAULT;
        break;
      case SGR_COMMAND_FRAKTUR:
        myIsFraktur = true;
        break;
      case SGR_COMMAND_DOUBLE_UNDERLINE:
        myWeight = Weight.DEFAULT;
        // this is ECMA-48 behaviour. Probably we could make this configurable via flag?
        myUnderline = DOUBLE_UNDERLINE;
        break;
      case SGR_COMMAND_NO_BOLD_FAINT:
        myWeight = Weight.DEFAULT;
        break;
      case SGR_COMMAND_NO_ITALIC_FRAKTUR:
        myIsFraktur = false;
        myIsItalic = false;
        break;
      case SGR_COMMAND_NO_UNDERLINE:
        myUnderline = Underline.DEFAULT;
        break;
      case SGR_COMMAND_NO_BLINK:
        myBlink = BlinkSpeed.DEFAULT;
        break;
      case SGR_COMMAND_NO_INVERSE:
        myIsInverse = false;
        break;
      case SGR_COMMAND_REVEAL:
        myIsConseal = false;
        break;
      case SGR_COMMAND_NO_CROSS_OUT:
        myIsCrossedOut = false;
        break;
      case SGR_COMMAND_FG_COLOR_ENCODED:
        myForegroundColor = decodeColor(sgrCodesIterator);
        break;
      case SGR_COMMAND_FG_COLOR_DEFAULT:
        myForegroundColor = null;
        break;
      case SGR_COMMAND_BG_COLOR_ENCODED:
        myBackgroundColor = decodeColor(sgrCodesIterator);
        break;
      case SGR_COMMAND_BG_COLOR_DEFAULT:
        myBackgroundColor = null;
        break;
      case SGR_COMMAND_FRAMED:
        myFrameType = FRAMED;
        break;
      case SGR_COMMAND_ENCIRCLED:
        myFrameType = ENCIRCLED;
        break;
      case SGR_COMMAND_OVERLINED:
        myIsOverlined = true;
        break;
      case SGR_COMMAND_NO_FRAMED_ENCIRCLED:
        myFrameType = NO_FRAME;
        break;
      case SGR_COMMAND_NO_OVERLINED:
        myIsOverlined = false;
        break;
      default:
        if (commandCode >= SGR_COMMAND_FONT1 && commandCode <= SGR_COMMAND_FONT9) {
          myFont = TerminalFont.values()[commandCode - 10];
        }
        else if (commandCode >= SGR_COMMAND_FG_COLOR0 && commandCode <= SGR_COMMAND_FG_COLOR7) {
          myForegroundColor = new EightBitColor(commandCode - SGR_COMMAND_FG_COLOR0);
        }
        else if (commandCode >= SGR_COMMAND_BG_COLOR0 && commandCode <= SGR_COMMAND_BG_COLOR7) {
          myBackgroundColor = new EightBitColor(commandCode - SGR_COMMAND_BG_COLOR0);
        }
        else if (commandCode >= SGR_COMMAND_IDEOGRAM_UNDER_RIGHT && commandCode <= SGR_COMMAND_IDEOGRAM_OFF) {
          LOG.debug("Ignore no-op ideogram sequence: " + commandCode);
        }
        else if (commandCode >= SGR_COMMAND_FG_COLOR8 && commandCode <= SGR_COMMAND_FG_COLOR15) {
          myForegroundColor = new EightBitColor(commandCode - SGR_COMMAND_FG_COLOR8 + 8);
        }
        else if (commandCode >= SGR_COMMAND_BG_COLOR8 && commandCode <= SGR_COMMAND_BG_COLOR15) {
          myBackgroundColor = new EightBitColor(commandCode - SGR_COMMAND_BG_COLOR8 + 8);
        }
        else {
          LOG.warn("Unknown command " + commandCode);
          return;
        }
    }
    mySerializedSgrStateProvider.drop();
  }

  /**
   * @return an ANSI string representing the SGR state of the current emulator. Same state is always represented with the same string.
   */
  @NotNull
  public String getAnsiSerializedSGRState() {
    return Objects.requireNonNull(mySerializedSgrStateProvider.getValue());
  }

  /**
   * @return an ANSI string representing the SGR state of the current emulator. Same state is always represented with the same string.
   */
  @NotNull
  public String computeAnsiSerializedSGRState() {
    List<String> state = new ArrayList<>();
    IntConsumer codeConsumer = it -> state.add(Integer.toString(it));

    codeConsumer.accept(SGR_COMMAND_RESET);
    if (myFont != TerminalFont.DEFAULT) {
      codeConsumer.accept(myFont.sgrCode);
    }
    if (myWeight != Weight.DEFAULT) {
      codeConsumer.accept(myWeight.sgrCode);
    }
    if (myUnderline != Underline.DEFAULT) {
      codeConsumer.accept(myUnderline.sgrCode);
    }
    if (myFont != TerminalFont.DEFAULT) {
      codeConsumer.accept(myFont.sgrCode);
    }
    if (myBlink != BlinkSpeed.DEFAULT) {
      codeConsumer.accept(myBlink.sgrCode);
    }
    if (myIsInverse) {
      codeConsumer.accept(SGR_COMMAND_INVERSE);
    }
    if (myBackgroundColor != null) {
      codeConsumer.accept(SGR_COMMAND_BG_COLOR_ENCODED);
      state.add(myBackgroundColor.getAnsiEncodedColor());
    }
    if (myForegroundColor != null) {
      codeConsumer.accept(SGR_COMMAND_FG_COLOR_ENCODED);
      state.add(myForegroundColor.getAnsiEncodedColor());
    }
    if (myIsItalic) {
      codeConsumer.accept(SGR_COMMAND_ITALIC);
    }
    if (myIsConseal) {
      codeConsumer.accept(SGR_COMMAND_CONCEAL);
    }
    if (myIsCrossedOut) {
      codeConsumer.accept(SGR_COMMAND_CROSS_OUT);
    }
    if (myIsFraktur) {
      codeConsumer.accept(SGR_COMMAND_FRAKTUR);
    }
    if (myFrameType != FrameType.DEFAULT) {
      codeConsumer.accept(myFrameType.sgrCode);
    }
    if (myIsOverlined) {
      codeConsumer.accept(SGR_COMMAND_OVERLINED);
    }
    return AnsiStreamingLexer.CSI + StringUtil.join(state, ";") + SGR_SUFFIX;
  }

  /**
   * Reset terminal values to defaults
   */
  private void resetTerminal() {
    myFont = EMPTY_EMULATOR.myFont;
    myWeight = EMPTY_EMULATOR.myWeight;
    myUnderline = EMPTY_EMULATOR.myUnderline;
    myBackgroundColor = EMPTY_EMULATOR.myBackgroundColor;
    myForegroundColor = EMPTY_EMULATOR.myForegroundColor;
    myFrameType = EMPTY_EMULATOR.myFrameType;
    myBlink = EMPTY_EMULATOR.myBlink;
    myIsInverse = EMPTY_EMULATOR.myIsInverse;
    myIsItalic = EMPTY_EMULATOR.myIsItalic;
    myIsConseal = EMPTY_EMULATOR.myIsConseal;
    myIsCrossedOut = EMPTY_EMULATOR.myIsCrossedOut;
    myIsFraktur = EMPTY_EMULATOR.myIsFraktur;
    myIsOverlined = EMPTY_EMULATOR.myIsOverlined;
    mySerializedSgrStateProvider.drop();
  }

  /**
   * @return true if emulator is in it's initial state
   * @see #resetTerminal()
   */
  public boolean isInitialState() {
    return EMPTY_EMULATOR.equals(this);
  }

  @Nullable
  public Color getBackgroundColor() {
    return myBackgroundColor == null ? null : myBackgroundColor.getColor();
  }

  public int getBackgroundColorIndex() {
    return myBackgroundColor == null ? -1 : myBackgroundColor.getColorIndex();
  }

  @Nullable
  public Color getForegroundColor() {
    return myForegroundColor == null ? null : myForegroundColor.getColor();
  }

  public int getForegroundColorIndex() {
    return myForegroundColor == null ? -1 : myForegroundColor.getColorIndex();
  }

  public boolean isInverse() {
    return myIsInverse;
  }

  @NotNull
  public Underline getUnderline() {
    return myUnderline;
  }

  @NotNull
  public FrameType getFrameType() {
    return myFrameType;
  }

  public boolean isCrossedOut() {
    return myIsCrossedOut;
  }

  @NotNull
  public Weight getWeight() {
    return myWeight;
  }

  public boolean isItalic() {
    return myIsItalic;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AnsiTerminalEmulator emulator = (AnsiTerminalEmulator)o;

    if (myIsItalic != emulator.myIsItalic) return false;
    if (myIsInverse != emulator.myIsInverse) return false;
    if (myIsConseal != emulator.myIsConseal) return false;
    if (myIsCrossedOut != emulator.myIsCrossedOut) return false;
    if (myIsFraktur != emulator.myIsFraktur) return false;
    if (myIsOverlined != emulator.myIsOverlined) return false;
    if (myFont != emulator.myFont) return false;
    if (myWeight != emulator.myWeight) return false;
    if (myUnderline != emulator.myUnderline) return false;
    if (myBlink != emulator.myBlink) return false;
    if (myFrameType != emulator.myFrameType) return false;
    if (!Objects.equals(myBackgroundColor, emulator.myBackgroundColor)) {
      return false;
    }
    if (!Objects.equals(myForegroundColor, emulator.myForegroundColor)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFont.hashCode();
    result = 31 * result + myWeight.hashCode();
    result = 31 * result + myUnderline.hashCode();
    result = 31 * result + myBlink.hashCode();
    result = 31 * result + myFrameType.hashCode();
    result = 31 * result + (myIsItalic ? 1 : 0);
    result = 31 * result + (myIsInverse ? 1 : 0);
    result = 31 * result + (myIsConseal ? 1 : 0);
    result = 31 * result + (myIsCrossedOut ? 1 : 0);
    result = 31 * result + (myIsFraktur ? 1 : 0);
    result = 31 * result + (myIsOverlined ? 1 : 0);
    result = 31 * result + (myBackgroundColor != null ? myBackgroundColor.hashCode() : 0);
    result = 31 * result + (myForegroundColor != null ? myForegroundColor.hashCode() : 0);
    return result;
  }

  /**
   * Decodes ANSI SGR sequences 38 && 48. Encodings may be:
   * <ul>
   * <li><a href="https://en.wikipedia.org/wiki/ANSI_escape_code#8-bit">5;n</a> - with index-based encoding</li>
   * <li><a href="https://en.wikipedia.org/wiki/ANSI_escape_code#24-bit">2;r;g;b</a> - with red, green and blue parts</li>
   * </ul>
   */
  private static AnsiTerminalColor decodeColor(@NotNull Iterator<Integer> encodedColorIterator) {
    if (!encodedColorIterator.hasNext()) {
      return null;
    }
    int encodingType = encodedColorIterator.next();
    if (encodingType == SGR_COLOR_ENCODING_RGB) {
      Integer redCode = encodedColorIterator.hasNext() ? encodedColorIterator.next() : null;
      Integer greenCode = encodedColorIterator.hasNext() ? encodedColorIterator.next() : null;
      Integer blueCode = encodedColorIterator.hasNext() ? encodedColorIterator.next() : null;
      if (redCode != null && greenCode != null && blueCode != null) {
        return new RGBColor(redCode, greenCode, blueCode);
      }
    }
    else if (encodingType == SGR_COLOR_ENCODING_INDEXED) {
      Integer encodedColor = encodedColorIterator.hasNext() ? encodedColorIterator.next() : null;
      if (encodedColor != null) {
        return decode8BitColor(encodedColor);
      }
    }

    return null;
  }

  /**
   * Decodes index-encoded color from SGR
   *
   * @see <a href="https://en.wikipedia.org/wiki/ANSI_escape_code#8-bit">5;n</a> - with index-based encoding
   */
  private static AnsiTerminalColor decode8BitColor(int encodedColor) {
    if (encodedColor >= 0 && encodedColor <= MAX_8BIT_COLOR_INDEX) {
      return new EightBitColor(encodedColor);
    }
    else if (encodedColor >= COLOR_CUBE_MIN_INDEX && encodedColor <= 255) {
      //16-231:  6 × 6 × 6 cube (216 colors): 16 + 36 × r + 6 × g + b (0 ≤ r, g, b ≤ 5)
      //232-255:  grayscale from black to white in 24 steps
      return new EightBitRGBColor(encodedColor);
    }
    return null;
  }

  public enum FrameType {
    NO_FRAME(SGR_COMMAND_NO_FRAMED_ENCIRCLED, null),
    FRAMED(SGR_COMMAND_FRAMED, EffectType.BOXED),
    ENCIRCLED(SGR_COMMAND_ENCIRCLED, EffectType.ROUNDED_BOX);

    private static final FrameType DEFAULT = NO_FRAME;
    /**
     * SGR sequence code that should be used to switch to this option
     */
    public final int sgrCode;

    @Nullable
    public final EffectType effectType;

    FrameType(int sgrCode, @Nullable EffectType effectType) {
      this.sgrCode = sgrCode;
      this.effectType = effectType;
    }
  }

  public enum BlinkSpeed {
    NO_BLINK(SGR_COMMAND_NO_BLINK),
    SLOW_BLINK(SGR_COMMAND_BLINK_SLOW),
    RAPID_BLINK(SGR_COMMAND_BLINK_RAPID);

    private static final BlinkSpeed DEFAULT = NO_BLINK;
    /**
     * SGR sequence code that should be used to switch to this option
     */
    public final int sgrCode;

    BlinkSpeed(int sgrCode) {
      this.sgrCode = sgrCode;
    }
  }

  public enum Weight {
    FAINT(SGR_COMMAND_FAINT),
    NORMAL(SGR_COMMAND_NO_BOLD_FAINT),
    BOLD(SGR_COMMAND_BOLD);

    private static final Weight DEFAULT = NORMAL;
    /**
     * SGR sequence code that should be used to switch to this option
     */
    public final int sgrCode;

    Weight(int sgrCode) {
      this.sgrCode = sgrCode;
    }
  }

  public enum Underline {
    NO_UNDERLINE(SGR_COMMAND_NO_UNDERLINE, null),
    SINGLE_UNDERLINE(SGR_COMMAND_UNDERLINE, LINE_UNDERSCORE),
    DOUBLE_UNDERLINE(SGR_COMMAND_DOUBLE_UNDERLINE, BOLD_LINE_UNDERSCORE);

    private static final Underline DEFAULT = NO_UNDERLINE;
    /**
     * SGR sequence code that should be used to switch to this option
     */
    public final int sgrCode;

    @Nullable
    public final EffectType effectType;

    Underline(int sgrCode, @Nullable EffectType effectType) {
      this.sgrCode = sgrCode;
      this.effectType = effectType;
    }
  }

  enum TerminalFont {
    DEFAULT(SGR_COMMAND_PRIMARY_FONT),
    ALT1(SGR_COMMAND_FONT1),
    ALT2(SGR_COMMAND_FONT2),
    ALT3(SGR_COMMAND_FONT3),
    ALT4(SGR_COMMAND_FONT4),
    ALT5(SGR_COMMAND_FONT5),
    ALT6(SGR_COMMAND_FONT6),
    ALT7(SGR_COMMAND_FONT7),
    ALT8(SGR_COMMAND_FONT8),
    ALT9(SGR_COMMAND_FONT9);
    /**
     * SGR sequence code that should be used to switch to this option
     */
    public final int sgrCode;

    TerminalFont(int sgrCode) {
      this.sgrCode = sgrCode;
    }
  }

  /**
   * Represents a 24 bit color, encoded with {@link AnsiCommands#SGR_COLOR_ENCODING_RGB}
   */
  private static final class RGBColor implements AnsiTerminalColor {
    private final int myRed;
    private final int myGreen;
    private final int myBlue;

    private RGBColor(int red, int green, int blue) {
      myRed = red;
      myGreen = green;
      myBlue = blue;
    }

    @NotNull
    @Override
    public String getAnsiEncodedColor() {
      return SGR_COLOR_ENCODING_RGB + ";" + myRed + ";" + myGreen + ";" + myBlue;
    }


    @Override
    public int getColorIndex() {
      return -1;
    }

    @NotNull
    @Override
    public Color getColor() {
      //noinspection UseJBColor
      return new Color(myRed, myGreen, myBlue);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      RGBColor color = (RGBColor)o;

      if (myRed != color.myRed) return false;
      if (myGreen != color.myGreen) return false;
      if (myBlue != color.myBlue) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myRed;
      result = 31 * result + myGreen;
      result = 31 * result + myBlue;
      return result;
    }
  }

  /**
   * Base class for index-based colors - 8bit and 8bit rgb colors
   */
  private abstract static class IndexedAnsiTerminalColor implements AnsiTerminalColor {
    private final int myColorIndex;

    protected IndexedAnsiTerminalColor(int colorIndex) {
      myColorIndex = colorIndex;
    }

    @Override
    public int getColorIndex() {
      return myColorIndex;
    }

    @NotNull
    @Override
    public String getAnsiEncodedColor() {
      return SGR_COLOR_ENCODING_INDEXED + ";" + getColorIndex();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      IndexedAnsiTerminalColor color = (IndexedAnsiTerminalColor)o;

      if (myColorIndex != color.myColorIndex) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myColorIndex;
    }
  }

  /**
   * Represents a simple indexed color for SGR commands:
   * <ul>
   * <li>{@link AnsiCommands#SGR_COMMAND_FG_COLOR0} - {@link AnsiCommands#SGR_COMMAND_FG_COLOR7}</li>
   * <li>{@link AnsiCommands#SGR_COMMAND_BG_COLOR0} - {@link AnsiCommands#SGR_COMMAND_BG_COLOR7}</li>
   * <li>{@link AnsiCommands#SGR_COMMAND_FG_COLOR8} - {@link AnsiCommands#SGR_COMMAND_FG_COLOR15}</li>
   * <li>{@link AnsiCommands#SGR_COMMAND_BG_COLOR8} - {@link AnsiCommands#SGR_COMMAND_BG_COLOR15}</li>
   * </ul>
   * Also used for first 15 indexed colors from {@link AnsiCommands#SGR_COMMAND_FG_COLOR_ENCODED} and {@link AnsiCommands#SGR_COMMAND_BG_COLOR_ENCODED}
   */
  private static final class EightBitColor extends IndexedAnsiTerminalColor {

    private EightBitColor(int colorIndex) {
      super(colorIndex);
      if (colorIndex < 0 || colorIndex > MAX_8BIT_COLOR_INDEX) {
        LOG.error("Wrong 8bit color index: " + colorIndex);
      }
    }

    @Nullable
    @Override
    public Color getColor() {
      return null;
    }
  }

  /**
   * Represents rgb colors encoded with {@link AnsiCommands#SGR_COLOR_ENCODING_INDEXED} from 16-231 and 232-255
   */
  private static final class EightBitRGBColor extends IndexedAnsiTerminalColor {

    // 8-bit color encoding for range 16-231
    private static final int[] CUBE_STEPS = {
      0x0, 0x5f, 0x87, 0xaf, 0xd7, 0xff
    };
    // 8-bit grayscale encoding for range 232-255
    private static final int[] GRAYSCALE_STEPS = {
      0x08, 0x12, 0x1c, 0x26, 0x30, 0x3a, 0x44, 0x4e,
      0x58, 0x62, 0x6c, 0x76, 0x80, 0x8a, 0x94, 0x9e,
      0xa8, 0xb2, 0xbc, 0xc6, 0xd0, 0xda, 0xe4, 0xee
    };

    private EightBitRGBColor(int colorIndex) {
      super(colorIndex);
      if (colorIndex < COLOR_CUBE_MIN_INDEX || colorIndex > 255) {
        LOG.error("Wrong indexed RGB color index: " + colorIndex);
      }
    }

    @Nullable
    @Override
    public Color getColor() {
      int colorIndex = getColorIndex();
      if (colorIndex >= COLOR_CUBE_MIN_INDEX && colorIndex < GRAY_MIN_INDEX) {
        int encodedColor = colorIndex - COLOR_CUBE_MIN_INDEX;
        //noinspection UseJBColor
        return new Color(CUBE_STEPS[(encodedColor / 36) % 6], CUBE_STEPS[(encodedColor / 6) % 6], CUBE_STEPS[encodedColor % 6]);
      }
      else if (colorIndex >= GRAY_MIN_INDEX && colorIndex <= 255) {
        int colorPart = GRAYSCALE_STEPS[colorIndex - GRAY_MIN_INDEX];
        //noinspection UseJBColor
        return new Color(colorPart, colorPart, colorPart);
      }
      return null;
    }
  }
}
