public final class BigIf {

  public static String getType(char shar) {
    if ('\u0000' <= shar && shar <= '\u001F')
      return "CONTROL";
    else  if (shar == '\u0020')
      return "SPACE_SEPARATOR";
    else  if ('\u0021' <= shar && shar <= '\u0023')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u0024')
      return "CURRENCY_SYMBOL";
    else  if ('\u0025' <= shar && shar <= '\'')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u0028')
      return "START_PUNCTUATION";
    else  if (shar == '\u0029')
      return "END_PUNCTUATION";
    else  if (shar == '\u002A')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u002B')
      return "MATH_SYMBOL";
    else  if (shar == '\u002C')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u002D')
      return "DASH_PUNCTUATION";
    else  if ('\u002E' <= shar && shar <= '\u002F')
      return "OTHER_PUNCTUATION";
    else  if ('\u0030' <= shar && shar <= '\u0039')
      return "DECIMAL_DIGIT_NUMBER";
    else  if ('\u003A' <= shar && shar <= '\u003B')
      return "OTHER_PUNCTUATION";
    else  if ('\u003C' <= shar && shar <= '\u003E')
      return "MATH_SYMBOL";
    else  if ('\u003F' <= shar && shar <= '\u0040')
      return "OTHER_PUNCTUATION";
    else  if ('\u0041' <= shar && shar <= '\u005A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u005B')
      return "START_PUNCTUATION";
    else  if (shar == '\\')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u005D')
      return "END_PUNCTUATION";
    else  if (shar == '\u005E')
      return "MODIFIER_SYMBOL";
    else  if (shar == '\u005F')
      return "CONNECTOR_PUNCTUATION";
    else  if (shar == '\u0060')
      return "MODIFIER_SYMBOL";
    else  if ('\u0061' <= shar && shar <= '\u007A')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u007B')
      return "START_PUNCTUATION";
    else  if (shar == '\u007C')
      return "MATH_SYMBOL";
    else  if (shar == '\u007D')
      return "END_PUNCTUATION";
    else  if (shar == '\u007E')
      return "MATH_SYMBOL";
    else  if ('\u007F' <= shar && shar <= '\u009F')
      return "CONTROL";
    else  if (shar == '\u00A0')
      return "SPACE_SEPARATOR";
    else  if (shar == '\u00A1')
      return "OTHER_PUNCTUATION";
    else  if ('\u00A2' <= shar && shar <= '\u00A5')
      return "CURRENCY_SYMBOL";
    else  if ('\u00A6' <= shar && shar <= '\u00A7')
      return "OTHER_SYMBOL";
    else  if (shar == '\u00A8')
      return "MODIFIER_SYMBOL";
    else  if (shar == '\u00A9')
      return "OTHER_SYMBOL";
    else  if (shar == '\u00AA')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u00AB')
      return "START_PUNCTUATION";
    else  if (shar == '\u00AC')
      return "MATH_SYMBOL";
    else  if (shar == '\u00AD')
      return "DASH_PUNCTUATION";
    else  if (shar == '\u00AE')
      return "OTHER_SYMBOL";
    else  if (shar == '\u00AF')
      return "MODIFIER_SYMBOL";
    else  if (shar == '\u00B0')
      return "OTHER_SYMBOL";
    else  if (shar == '\u00B1')
      return "MATH_SYMBOL";
    else  if ('\u00B2' <= shar && shar <= '\u00B3')
      return "OTHER_NUMBER";
    else  if (shar == '\u00B4')
      return "MODIFIER_SYMBOL";
    else  if (shar == '\u00B5')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u00B6')
      return "OTHER_SYMBOL";
    else  if (shar == '\u00B7')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u00B8')
      return "MODIFIER_SYMBOL";
    else  if (shar == '\u00B9')
      return "OTHER_NUMBER";
    else  if (shar == '\u00BA')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u00BB')
      return "END_PUNCTUATION";
    else  if ('\u00BC' <= shar && shar <= '\u00BE')
      return "OTHER_NUMBER";
    else  if (shar == '\u00BF')
      return "OTHER_PUNCTUATION";
    else  if ('\u00C0' <= shar && shar <= '\u00D6')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u00D7')
      return "MATH_SYMBOL";
    else  if ('\u00D8' <= shar && shar <= '\u00DE')
      return "UPPERCASE_LETTER";
    else  if ('\u00DF' <= shar && shar <= '\u00F6')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u00F7')
      return "MATH_SYMBOL";
    else  if ('\u00F8' <= shar && shar <= '\u00FF')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0100')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0101')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0102')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0103')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0104')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0105')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0106')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0107')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0108')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0109')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u010A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u010B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u010C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u010D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u010E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u010F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0110')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0111')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0112')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0113')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0114')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0115')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0116')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0117')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0118')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0119')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u011A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u011B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u011C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u011D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u011E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u011F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0120')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0121')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0122')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0123')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0124')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0125')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0126')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0127')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0128')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0129')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u012A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u012B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u012C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u012D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u012E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u012F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0130')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0131')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0132')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0133')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0134')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0135')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0136')
      return "UPPERCASE_LETTER";
    else  if ('\u0137' <= shar && shar <= '\u0138')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0139')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u013A')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u013B')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u013C')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u013D')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u013E')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u013F')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0140')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0141')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0142')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0143')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0144')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0145')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0146')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0147')
      return "UPPERCASE_LETTER";
    else  if ('\u0148' <= shar && shar <= '\u0149')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u014A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u014B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u014C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u014D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u014E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u014F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0150')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0151')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0152')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0153')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0154')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0155')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0156')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0157')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0158')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0159')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u015A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u015B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u015C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u015D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u015E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u015F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0160')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0161')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0162')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0163')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0164')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0165')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0166')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0167')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0168')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0169')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u016A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u016B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u016C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u016D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u016E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u016F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0170')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0171')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0172')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0173')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0174')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0175')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0176')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0177')
      return "LOWERCASE_LETTER";
    else  if ('\u0178' <= shar && shar <= '\u0179')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u017A')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u017B')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u017C')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u017D')
      return "UPPERCASE_LETTER";
    else  if ('\u017E' <= shar && shar <= '\u0180')
      return "LOWERCASE_LETTER";
    else  if ('\u0181' <= shar && shar <= '\u0182')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0183')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0184')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0185')
      return "LOWERCASE_LETTER";
    else  if ('\u0186' <= shar && shar <= '\u0187')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0188')
      return "LOWERCASE_LETTER";
    else  if ('\u0189' <= shar && shar <= '\u018B')
      return "UPPERCASE_LETTER";
    else  if ('\u018C' <= shar && shar <= '\u018D')
      return "LOWERCASE_LETTER";
    else  if ('\u018E' <= shar && shar <= '\u0191')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0192')
      return "LOWERCASE_LETTER";
    else  if ('\u0193' <= shar && shar <= '\u0194')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0195')
      return "LOWERCASE_LETTER";
    else  if ('\u0196' <= shar && shar <= '\u0198')
      return "UPPERCASE_LETTER";
    else  if ('\u0199' <= shar && shar <= '\u019B')
      return "LOWERCASE_LETTER";
    else  if ('\u019C' <= shar && shar <= '\u019D')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u019E')
      return "LOWERCASE_LETTER";
    else  if ('\u019F' <= shar && shar <= '\u01A0')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01A1')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01A2')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01A3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01A4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01A5')
      return "LOWERCASE_LETTER";
    else  if ('\u01A6' <= shar && shar <= '\u01A7')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01A8')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01A9')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01AA')
      return "OTHER_LETTER";
    else  if (shar == '\u01AB')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01AC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01AD')
      return "LOWERCASE_LETTER";
    else  if ('\u01AE' <= shar && shar <= '\u01AF')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01B0')
      return "LOWERCASE_LETTER";
    else  if ('\u01B1' <= shar && shar <= '\u01B3')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01B4')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01B5')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01B6')
      return "LOWERCASE_LETTER";
    else  if ('\u01B7' <= shar && shar <= '\u01B8')
      return "UPPERCASE_LETTER";
    else  if ('\u01B9' <= shar && shar <= '\u01BA')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01BB')
      return "OTHER_LETTER";
    else  if (shar == '\u01BC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01BD')
      return "LOWERCASE_LETTER";
    else  if ('\u01BE' <= shar && shar <= '\u01C3')
      return "OTHER_LETTER";
    else  if (shar == '\u01C4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01C5')
      return "TITLECASE_LETTER";
    else  if (shar == '\u01C6')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01C7')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01C8')
      return "TITLECASE_LETTER";
    else  if (shar == '\u01C9')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01CA')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01CB')
      return "TITLECASE_LETTER";
    else  if (shar == '\u01CC')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01CD')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01CE')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01CF')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01D0')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01D1')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01D2')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01D3')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01D4')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01D5')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01D6')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01D7')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01D8')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01D9')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01DA')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01DB')
      return "UPPERCASE_LETTER";
    else  if ('\u01DC' <= shar && shar <= '\u01DD')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01DE')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01DF')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01E0')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01E1')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01E2')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01E3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01E4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01E5')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01E6')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01E7')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01E8')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01E9')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01EA')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01EB')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01EC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01ED')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01EE')
      return "UPPERCASE_LETTER";
    else  if ('\u01EF' <= shar && shar <= '\u01F0')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01F1')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01F2')
      return "TITLECASE_LETTER";
    else  if (shar == '\u01F3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01F4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01F5')
      return "LOWERCASE_LETTER";
    else  if ('\u01F6' <= shar && shar <= '\u01F9')
      return "UNASSIGNED";
    else  if (shar == '\u01FA')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01FB')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01FC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01FD')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u01FE')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u01FF')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0200')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0201')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0202')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0203')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0204')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0205')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0206')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0207')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0208')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0209')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u020A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u020B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u020C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u020D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u020E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u020F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0210')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0211')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0212')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0213')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0214')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0215')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0216')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0217')
      return "LOWERCASE_LETTER";
    else  if ('\u0218' <= shar && shar <= '\u024F')
      return "UNASSIGNED";
    else  if ('\u0250' <= shar && shar <= '\u02A8')
      return "LOWERCASE_LETTER";
    else  if ('\u02A9' <= shar && shar <= '\u02AF')
      return "UNASSIGNED";
    else  if ('\u02B0' <= shar && shar <= '\u02B8')
      return "MODIFIER_LETTER";
    else  if ('\u02B9' <= shar && shar <= '\u02BA')
      return "MODIFIER_SYMBOL";
    else  if ('\u02BB' <= shar && shar <= '\u02C1')
      return "MODIFIER_LETTER";
    else  if ('\u02C2' <= shar && shar <= '\u02CF')
      return "MODIFIER_SYMBOL";
    else  if ('\u02D0' <= shar && shar <= '\u02D1')
      return "MODIFIER_LETTER";
    else  if ('\u02D2' <= shar && shar <= '\u02DE')
      return "MODIFIER_SYMBOL";
    else  if (shar == '\u02DF')
      return "UNASSIGNED";
    else  if ('\u02E0' <= shar && shar <= '\u02E4')
      return "MODIFIER_LETTER";
    else  if ('\u02E5' <= shar && shar <= '\u02E9')
      return "MODIFIER_SYMBOL";
    else  if ('\u02EA' <= shar && shar <= '\u02FF')
      return "UNASSIGNED";
    else  if ('\u0300' <= shar && shar <= '\u0345')
      return "NON_SPACING_MARK";
    else  if ('\u0346' <= shar && shar <= '\u035F')
      return "UNASSIGNED";
    else  if ('\u0360' <= shar && shar <= '\u0361')
      return "NON_SPACING_MARK";
    else  if ('\u0362' <= shar && shar <= '\u0373')
      return "UNASSIGNED";
    else  if ('\u0374' <= shar && shar <= '\u0375')
      return "OTHER_PUNCTUATION";
    else  if ('\u0376' <= shar && shar <= '\u0379')
      return "UNASSIGNED";
    else  if (shar == '\u037A')
      return "MODIFIER_LETTER";
    else  if ('\u037B' <= shar && shar <= '\u037D')
      return "UNASSIGNED";
    else  if (shar == '\u037E')
      return "OTHER_PUNCTUATION";
    else  if ('\u037F' <= shar && shar <= '\u0383')
      return "UNASSIGNED";
    else  if ('\u0384' <= shar && shar <= '\u0385')
      return "MODIFIER_SYMBOL";
    else  if (shar == '\u0386')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0387')
      return "OTHER_PUNCTUATION";
    else  if ('\u0388' <= shar && shar <= '\u038A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u038B')
      return "UNASSIGNED";
    else  if (shar == '\u038C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u038D')
      return "UNASSIGNED";
    else  if ('\u038E' <= shar && shar <= '\u038F')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0390')
      return "LOWERCASE_LETTER";
    else  if ('\u0391' <= shar && shar <= '\u03A1')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u03A2')
      return "UNASSIGNED";
    else  if ('\u03A3' <= shar && shar <= '\u03AB')
      return "UPPERCASE_LETTER";
    else  if ('\u03AC' <= shar && shar <= '\u03CE')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u03CF')
      return "UNASSIGNED";
    else  if ('\u03D0' <= shar && shar <= '\u03D1')
      return "LOWERCASE_LETTER";
    else  if ('\u03D2' <= shar && shar <= '\u03D4')
      return "UPPERCASE_LETTER";
    else  if ('\u03D5' <= shar && shar <= '\u03D6')
      return "LOWERCASE_LETTER";
    else  if ('\u03D7' <= shar && shar <= '\u03D9')
      return "UNASSIGNED";
    else  if (shar == '\u03DA')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u03DB')
      return "UNASSIGNED";
    else  if (shar == '\u03DC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u03DD')
      return "UNASSIGNED";
    else  if (shar == '\u03DE')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u03DF')
      return "UNASSIGNED";
    else  if (shar == '\u03E0')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u03E1')
      return "UNASSIGNED";
    else  if (shar == '\u03E2')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u03E3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u03E4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u03E5')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u03E6')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u03E7')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u03E8')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u03E9')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u03EA')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u03EB')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u03EC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u03ED')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u03EE')
      return "UPPERCASE_LETTER";
    else  if ('\u03EF' <= shar && shar <= '\u03F2')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u03F3')
      return "OTHER_LETTER";
    else  if ('\u03F4' <= shar && shar <= '\u0400')
      return "UNASSIGNED";
    else  if ('\u0401' <= shar && shar <= '\u040C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u040D')
      return "UNASSIGNED";
    else  if ('\u040E' <= shar && shar <= '\u042F')
      return "UPPERCASE_LETTER";
    else  if ('\u0430' <= shar && shar <= '\u044F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0450')
      return "UNASSIGNED";
    else  if ('\u0451' <= shar && shar <= '\u045C')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u045D')
      return "UNASSIGNED";
    else  if ('\u045E' <= shar && shar <= '\u045F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0460')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0461')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0462')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0463')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0464')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0465')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0466')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0467')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0468')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0469')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u046A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u046B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u046C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u046D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u046E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u046F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0470')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0471')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0472')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0473')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0474')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0475')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0476')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0477')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0478')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0479')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u047A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u047B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u047C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u047D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u047E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u047F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0480')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0481')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0482')
      return "OTHER_SYMBOL";
    else  if ('\u0483' <= shar && shar <= '\u0486')
      return "NON_SPACING_MARK";
    else  if ('\u0487' <= shar && shar <= '\u048F')
      return "UNASSIGNED";
    else  if (shar == '\u0490')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0491')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0492')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0493')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0494')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0495')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0496')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0497')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0498')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u0499')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u049A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u049B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u049C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u049D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u049E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u049F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04A0')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04A1')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04A2')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04A3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04A4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04A5')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04A6')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04A7')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04A8')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04A9')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04AA')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04AB')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04AC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04AD')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04AE')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04AF')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04B0')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04B1')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04B2')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04B3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04B4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04B5')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04B6')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04B7')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04B8')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04B9')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04BA')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04BB')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04BC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04BD')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04BE')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04BF')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04C0')
      return "OTHER_LETTER";
    else  if (shar == '\u04C1')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04C2')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04C3')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04C4')
      return "LOWERCASE_LETTER";
    else  if ('\u04C5' <= shar && shar <= '\u04C6')
      return "UNASSIGNED";
    else  if (shar == '\u04C7')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04C8')
      return "LOWERCASE_LETTER";
    else  if ('\u04C9' <= shar && shar <= '\u04CA')
      return "UNASSIGNED";
    else  if (shar == '\u04CB')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04CC')
      return "LOWERCASE_LETTER";
    else  if ('\u04CD' <= shar && shar <= '\u04CF')
      return "UNASSIGNED";
    else  if (shar == '\u04D0')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04D1')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04D2')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04D3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04D4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04D5')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04D6')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04D7')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04D8')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04D9')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04DA')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04DB')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04DC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04DD')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04DE')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04DF')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04E0')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04E1')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04E2')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04E3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04E4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04E5')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04E6')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04E7')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04E8')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04E9')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04EA')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04EB')
      return "LOWERCASE_LETTER";
    else  if ('\u04EC' <= shar && shar <= '\u04ED')
      return "UNASSIGNED";
    else  if (shar == '\u04EE')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04EF')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04F0')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04F1')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04F2')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04F3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u04F4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04F5')
      return "LOWERCASE_LETTER";
    else  if ('\u04F6' <= shar && shar <= '\u04F7')
      return "UNASSIGNED";
    else  if (shar == '\u04F8')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u04F9')
      return "LOWERCASE_LETTER";
    else  if ('\u04FA' <= shar && shar <= '\u0530')
      return "UNASSIGNED";
    else  if ('\u0531' <= shar && shar <= '\u0556')
      return "UPPERCASE_LETTER";
    else  if ('\u0557' <= shar && shar <= '\u0558')
      return "UNASSIGNED";
    else  if (shar == '\u0559')
      return "MODIFIER_LETTER";
    else  if ('\u055A' <= shar && shar <= '\u055F')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u0560')
      return "UNASSIGNED";
    else  if ('\u0561' <= shar && shar <= '\u0587')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u0588')
      return "UNASSIGNED";
    else  if (shar == '\u0589')
      return "OTHER_PUNCTUATION";
    else  if ('\u058A' <= shar && shar <= '\u0590')
      return "UNASSIGNED";
    else  if ('\u0591' <= shar && shar <= '\u05A1')
      return "NON_SPACING_MARK";
    else  if (shar == '\u05A2')
      return "UNASSIGNED";
    else  if ('\u05A3' <= shar && shar <= '\u05B9')
      return "NON_SPACING_MARK";
    else  if (shar == '\u05BA')
      return "UNASSIGNED";
    else  if ('\u05BB' <= shar && shar <= '\u05BD')
      return "NON_SPACING_MARK";
    else  if (shar == '\u05BE')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u05BF')
      return "NON_SPACING_MARK";
    else  if (shar == '\u05C0')
      return "OTHER_PUNCTUATION";
    else  if ('\u05C1' <= shar && shar <= '\u05C2')
      return "NON_SPACING_MARK";
    else  if (shar == '\u05C3')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u05C4')
      return "NON_SPACING_MARK";
    else  if ('\u05C5' <= shar && shar <= '\u05CF')
      return "UNASSIGNED";
    else  if ('\u05D0' <= shar && shar <= '\u05EA')
      return "OTHER_LETTER";
    else  if ('\u05EB' <= shar && shar <= '\u05EF')
      return "UNASSIGNED";
    else  if ('\u05F0' <= shar && shar <= '\u05F2')
      return "OTHER_LETTER";
    else  if ('\u05F3' <= shar && shar <= '\u05F4')
      return "OTHER_PUNCTUATION";
    else  if ('\u05F5' <= shar && shar <= '\u060B')
      return "UNASSIGNED";
    else  if (shar == '\u060C')
      return "OTHER_PUNCTUATION";
    else  if ('\u060D' <= shar && shar <= '\u061A')
      return "UNASSIGNED";
    else  if (shar == '\u061B')
      return "OTHER_PUNCTUATION";
    else  if ('\u061C' <= shar && shar <= '\u061E')
      return "UNASSIGNED";
    else  if (shar == '\u061F')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u0620')
      return "UNASSIGNED";
    else  if ('\u0621' <= shar && shar <= '\u063A')
      return "OTHER_LETTER";
    else  if ('\u063B' <= shar && shar <= '\u063F')
      return "UNASSIGNED";
    else  if (shar == '\u0640')
      return "MODIFIER_LETTER";
    else  if ('\u0641' <= shar && shar <= '\u064A')
      return "OTHER_LETTER";
    else  if ('\u064B' <= shar && shar <= '\u0652')
      return "NON_SPACING_MARK";
    else  if ('\u0653' <= shar && shar <= '\u065F')
      return "UNASSIGNED";
    else  if ('\u0660' <= shar && shar <= '\u0669')
      return "DECIMAL_DIGIT_NUMBER";
    else  if ('\u066A' <= shar && shar <= '\u066D')
      return "OTHER_PUNCTUATION";
    else  if ('\u066E' <= shar && shar <= '\u066F')
      return "UNASSIGNED";
    else  if (shar == '\u0670')
      return "NON_SPACING_MARK";
    else  if ('\u0671' <= shar && shar <= '\u06B7')
      return "OTHER_LETTER";
    else  if ('\u06B8' <= shar && shar <= '\u06B9')
      return "UNASSIGNED";
    else  if ('\u06BA' <= shar && shar <= '\u06BE')
      return "OTHER_LETTER";
    else  if (shar == '\u06BF')
      return "UNASSIGNED";
    else  if ('\u06C0' <= shar && shar <= '\u06CE')
      return "OTHER_LETTER";
    else  if (shar == '\u06CF')
      return "UNASSIGNED";
    else  if ('\u06D0' <= shar && shar <= '\u06D3')
      return "OTHER_LETTER";
    else  if (shar == '\u06D4')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u06D5')
      return "OTHER_LETTER";
    else  if ('\u06D6' <= shar && shar <= '\u06DC')
      return "NON_SPACING_MARK";
    else  if ('\u06DD' <= shar && shar <= '\u06DE')
      return "ENCLOSING_MARK";
    else  if ('\u06DF' <= shar && shar <= '\u06E4')
      return "NON_SPACING_MARK";
    else  if ('\u06E5' <= shar && shar <= '\u06E6')
      return "MODIFIER_LETTER";
    else  if ('\u06E7' <= shar && shar <= '\u06E8')
      return "NON_SPACING_MARK";
    else  if (shar == '\u06E9')
      return "OTHER_SYMBOL";
    else  if ('\u06EA' <= shar && shar <= '\u06ED')
      return "NON_SPACING_MARK";
    else  if ('\u06EE' <= shar && shar <= '\u06EF')
      return "UNASSIGNED";
    else  if ('\u06F0' <= shar && shar <= '\u06F9')
      return "DECIMAL_DIGIT_NUMBER";
    else  if ('\u06FA' <= shar && shar <= '\u0900')
      return "UNASSIGNED";
    else  if ('\u0901' <= shar && shar <= '\u0902')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0903')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0904')
      return "UNASSIGNED";
    else  if ('\u0905' <= shar && shar <= '\u0939')
      return "OTHER_LETTER";
    else  if ('\u093A' <= shar && shar <= '\u093B')
      return "UNASSIGNED";
    else  if (shar == '\u093C')
      return "NON_SPACING_MARK";
    else  if (shar == '\u093D')
      return "OTHER_LETTER";
    else  if ('\u093E' <= shar && shar <= '\u0940')
      return "COMBINING_SPACING_MARK";
    else  if ('\u0941' <= shar && shar <= '\u0948')
      return "NON_SPACING_MARK";
    else  if ('\u0949' <= shar && shar <= '\u094C')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u094D')
      return "NON_SPACING_MARK";
    else  if ('\u094E' <= shar && shar <= '\u094F')
      return "UNASSIGNED";
    else  if (shar == '\u0950')
      return "OTHER_SYMBOL";
    else  if ('\u0951' <= shar && shar <= '\u0954')
      return "NON_SPACING_MARK";
    else  if ('\u0955' <= shar && shar <= '\u0957')
      return "UNASSIGNED";
    else  if ('\u0958' <= shar && shar <= '\u0961')
      return "OTHER_LETTER";
    else  if ('\u0962' <= shar && shar <= '\u0963')
      return "NON_SPACING_MARK";
    else  if ('\u0964' <= shar && shar <= '\u0965')
      return "OTHER_PUNCTUATION";
    else  if ('\u0966' <= shar && shar <= '\u096F')
      return "DECIMAL_DIGIT_NUMBER";
    else  if (shar == '\u0970')
      return "OTHER_PUNCTUATION";
    else  if ('\u0971' <= shar && shar <= '\u0980')
      return "UNASSIGNED";
    else  if (shar == '\u0981')
      return "NON_SPACING_MARK";
    else  if ('\u0982' <= shar && shar <= '\u0983')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0984')
      return "UNASSIGNED";
    else  if ('\u0985' <= shar && shar <= '\u098C')
      return "OTHER_LETTER";
    else  if ('\u098D' <= shar && shar <= '\u098E')
      return "UNASSIGNED";
    else  if ('\u098F' <= shar && shar <= '\u0990')
      return "OTHER_LETTER";
    else  if ('\u0991' <= shar && shar <= '\u0992')
      return "UNASSIGNED";
    else  if ('\u0993' <= shar && shar <= '\u09A8')
      return "OTHER_LETTER";
    else  if (shar == '\u09A9')
      return "UNASSIGNED";
    else  if ('\u09AA' <= shar && shar <= '\u09B0')
      return "OTHER_LETTER";
    else  if (shar == '\u09B1')
      return "UNASSIGNED";
    else  if (shar == '\u09B2')
      return "OTHER_LETTER";
    else  if ('\u09B3' <= shar && shar <= '\u09B5')
      return "UNASSIGNED";
    else  if ('\u09B6' <= shar && shar <= '\u09B9')
      return "OTHER_LETTER";
    else  if ('\u09BA' <= shar && shar <= '\u09BB')
      return "UNASSIGNED";
    else  if (shar == '\u09BC')
      return "NON_SPACING_MARK";
    else  if (shar == '\u09BD')
      return "UNASSIGNED";
    else  if ('\u09BE' <= shar && shar <= '\u09C0')
      return "COMBINING_SPACING_MARK";
    else  if ('\u09C1' <= shar && shar <= '\u09C4')
      return "NON_SPACING_MARK";
    else  if ('\u09C5' <= shar && shar <= '\u09C6')
      return "UNASSIGNED";
    else  if ('\u09C7' <= shar && shar <= '\u09C8')
      return "COMBINING_SPACING_MARK";
    else  if ('\u09C9' <= shar && shar <= '\u09CA')
      return "UNASSIGNED";
    else  if ('\u09CB' <= shar && shar <= '\u09CC')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u09CD')
      return "NON_SPACING_MARK";
    else  if ('\u09CE' <= shar && shar <= '\u09D6')
      return "UNASSIGNED";
    else  if (shar == '\u09D7')
      return "COMBINING_SPACING_MARK";
    else  if ('\u09D8' <= shar && shar <= '\u09DB')
      return "UNASSIGNED";
    else  if ('\u09DC' <= shar && shar <= '\u09DD')
      return "OTHER_LETTER";
    else  if (shar == '\u09DE')
      return "UNASSIGNED";
    else  if ('\u09DF' <= shar && shar <= '\u09E1')
      return "OTHER_LETTER";
    else  if ('\u09E2' <= shar && shar <= '\u09E3')
      return "NON_SPACING_MARK";
    else  if ('\u09E4' <= shar && shar <= '\u09E5')
      return "UNASSIGNED";
    else  if ('\u09E6' <= shar && shar <= '\u09EF')
      return "DECIMAL_DIGIT_NUMBER";
    else  if ('\u09F0' <= shar && shar <= '\u09F1')
      return "OTHER_LETTER";
    else  if ('\u09F2' <= shar && shar <= '\u09F3')
      return "CURRENCY_SYMBOL";
    else  if ('\u09F4' <= shar && shar <= '\u09F9')
      return "OTHER_NUMBER";
    else  if (shar == '\u09FA')
      return "OTHER_SYMBOL";
    else  if ('\u09FB' <= shar && shar <= '\u0A01')
      return "UNASSIGNED";
    else  if (shar == '\u0A02')
      return "NON_SPACING_MARK";
    else  if ('\u0A03' <= shar && shar <= '\u0A04')
      return "UNASSIGNED";
    else  if ('\u0A05' <= shar && shar <= '\u0A0A')
      return "OTHER_LETTER";
    else  if ('\u0A0B' <= shar && shar <= '\u0A0E')
      return "UNASSIGNED";
    else  if ('\u0A0F' <= shar && shar <= '\u0A10')
      return "OTHER_LETTER";
    else  if ('\u0A11' <= shar && shar <= '\u0A12')
      return "UNASSIGNED";
    else  if ('\u0A13' <= shar && shar <= '\u0A28')
      return "OTHER_LETTER";
    else  if (shar == '\u0A29')
      return "UNASSIGNED";
    else  if ('\u0A2A' <= shar && shar <= '\u0A30')
      return "OTHER_LETTER";
    else  if (shar == '\u0A31')
      return "UNASSIGNED";
    else  if ('\u0A32' <= shar && shar <= '\u0A33')
      return "OTHER_LETTER";
    else  if (shar == '\u0A34')
      return "UNASSIGNED";
    else  if ('\u0A35' <= shar && shar <= '\u0A36')
      return "OTHER_LETTER";
    else  if (shar == '\u0A37')
      return "UNASSIGNED";
    else  if ('\u0A38' <= shar && shar <= '\u0A39')
      return "OTHER_LETTER";
    else  if ('\u0A3A' <= shar && shar <= '\u0A3B')
      return "UNASSIGNED";
    else  if (shar == '\u0A3C')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0A3D')
      return "UNASSIGNED";
    else  if ('\u0A3E' <= shar && shar <= '\u0A40')
      return "COMBINING_SPACING_MARK";
    else  if ('\u0A41' <= shar && shar <= '\u0A42')
      return "NON_SPACING_MARK";
    else  if ('\u0A43' <= shar && shar <= '\u0A46')
      return "UNASSIGNED";
    else  if ('\u0A47' <= shar && shar <= '\u0A48')
      return "NON_SPACING_MARK";
    else  if ('\u0A49' <= shar && shar <= '\u0A4A')
      return "UNASSIGNED";
    else  if ('\u0A4B' <= shar && shar <= '\u0A4D')
      return "NON_SPACING_MARK";
    else  if ('\u0A4E' <= shar && shar <= '\u0A58')
      return "UNASSIGNED";
    else  if ('\u0A59' <= shar && shar <= '\u0A5C')
      return "OTHER_LETTER";
    else  if (shar == '\u0A5D')
      return "UNASSIGNED";
    else  if (shar == '\u0A5E')
      return "OTHER_LETTER";
    else  if ('\u0A5F' <= shar && shar <= '\u0A65')
      return "UNASSIGNED";
    else  if ('\u0A66' <= shar && shar <= '\u0A6F')
      return "DECIMAL_DIGIT_NUMBER";
    else  if ('\u0A70' <= shar && shar <= '\u0A71')
      return "NON_SPACING_MARK";
    else  if ('\u0A72' <= shar && shar <= '\u0A74')
      return "OTHER_LETTER";
    else  if ('\u0A75' <= shar && shar <= '\u0A80')
      return "UNASSIGNED";
    else  if ('\u0A81' <= shar && shar <= '\u0A82')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0A83')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0A84')
      return "UNASSIGNED";
    else  if ('\u0A85' <= shar && shar <= '\u0A8B')
      return "OTHER_LETTER";
    else  if (shar == '\u0A8C')
      return "UNASSIGNED";
    else  if (shar == '\u0A8D')
      return "OTHER_LETTER";
    else  if (shar == '\u0A8E')
      return "UNASSIGNED";
    else  if ('\u0A8F' <= shar && shar <= '\u0A91')
      return "OTHER_LETTER";
    else  if (shar == '\u0A92')
      return "UNASSIGNED";
    else  if ('\u0A93' <= shar && shar <= '\u0AA8')
      return "OTHER_LETTER";
    else  if (shar == '\u0AA9')
      return "UNASSIGNED";
    else  if ('\u0AAA' <= shar && shar <= '\u0AB0')
      return "OTHER_LETTER";
    else  if (shar == '\u0AB1')
      return "UNASSIGNED";
    else  if ('\u0AB2' <= shar && shar <= '\u0AB3')
      return "OTHER_LETTER";
    else  if (shar == '\u0AB4')
      return "UNASSIGNED";
    else  if ('\u0AB5' <= shar && shar <= '\u0AB9')
      return "OTHER_LETTER";
    else  if ('\u0ABA' <= shar && shar <= '\u0ABB')
      return "UNASSIGNED";
    else  if (shar == '\u0ABC')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0ABD')
      return "OTHER_LETTER";
    else  if ('\u0ABE' <= shar && shar <= '\u0AC0')
      return "COMBINING_SPACING_MARK";
    else  if ('\u0AC1' <= shar && shar <= '\u0AC5')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0AC6')
      return "UNASSIGNED";
    else  if ('\u0AC7' <= shar && shar <= '\u0AC8')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0AC9')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0ACA')
      return "UNASSIGNED";
    else  if ('\u0ACB' <= shar && shar <= '\u0ACC')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0ACD')
      return "NON_SPACING_MARK";
    else  if ('\u0ACE' <= shar && shar <= '\u0ACF')
      return "UNASSIGNED";
    else  if (shar == '\u0AD0')
      return "OTHER_SYMBOL";
    else  if ('\u0AD1' <= shar && shar <= '\u0ADF')
      return "UNASSIGNED";
    else  if (shar == '\u0AE0')
      return "OTHER_LETTER";
    else  if ('\u0AE1' <= shar && shar <= '\u0AE5')
      return "UNASSIGNED";
    else  if ('\u0AE6' <= shar && shar <= '\u0AEF')
      return "DECIMAL_DIGIT_NUMBER";
    else  if ('\u0AF0' <= shar && shar <= '\u0B00')
      return "UNASSIGNED";
    else  if (shar == '\u0B01')
      return "NON_SPACING_MARK";
    else  if ('\u0B02' <= shar && shar <= '\u0B03')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0B04')
      return "UNASSIGNED";
    else  if ('\u0B05' <= shar && shar <= '\u0B0C')
      return "OTHER_LETTER";
    else  if ('\u0B0D' <= shar && shar <= '\u0B0E')
      return "UNASSIGNED";
    else  if ('\u0B0F' <= shar && shar <= '\u0B10')
      return "OTHER_LETTER";
    else  if ('\u0B11' <= shar && shar <= '\u0B12')
      return "UNASSIGNED";
    else  if ('\u0B13' <= shar && shar <= '\u0B28')
      return "OTHER_LETTER";
    else  if (shar == '\u0B29')
      return "UNASSIGNED";
    else  if ('\u0B2A' <= shar && shar <= '\u0B30')
      return "OTHER_LETTER";
    else  if (shar == '\u0B31')
      return "UNASSIGNED";
    else  if ('\u0B32' <= shar && shar <= '\u0B33')
      return "OTHER_LETTER";
    else  if ('\u0B34' <= shar && shar <= '\u0B35')
      return "UNASSIGNED";
    else  if ('\u0B36' <= shar && shar <= '\u0B39')
      return "OTHER_LETTER";
    else  if ('\u0B3A' <= shar && shar <= '\u0B3B')
      return "UNASSIGNED";
    else  if (shar == '\u0B3C')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0B3D')
      return "OTHER_LETTER";
    else  if (shar == '\u0B3E')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0B3F')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0B40')
      return "COMBINING_SPACING_MARK";
    else  if ('\u0B41' <= shar && shar <= '\u0B43')
      return "NON_SPACING_MARK";
    else  if ('\u0B44' <= shar && shar <= '\u0B46')
      return "UNASSIGNED";
    else  if ('\u0B47' <= shar && shar <= '\u0B48')
      return "COMBINING_SPACING_MARK";
    else  if ('\u0B49' <= shar && shar <= '\u0B4A')
      return "UNASSIGNED";
    else  if ('\u0B4B' <= shar && shar <= '\u0B4C')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0B4D')
      return "NON_SPACING_MARK";
    else  if ('\u0B4E' <= shar && shar <= '\u0B55')
      return "UNASSIGNED";
    else  if (shar == '\u0B56')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0B57')
      return "COMBINING_SPACING_MARK";
    else  if ('\u0B58' <= shar && shar <= '\u0B5B')
      return "UNASSIGNED";
    else  if ('\u0B5C' <= shar && shar <= '\u0B5D')
      return "OTHER_LETTER";
    else  if (shar == '\u0B5E')
      return "UNASSIGNED";
    else  if ('\u0B5F' <= shar && shar <= '\u0B61')
      return "OTHER_LETTER";
    else  if ('\u0B62' <= shar && shar <= '\u0B65')
      return "UNASSIGNED";
    else  if ('\u0B66' <= shar && shar <= '\u0B6F')
      return "DECIMAL_DIGIT_NUMBER";
    else  if (shar == '\u0B70')
      return "OTHER_SYMBOL";
    else  if ('\u0B71' <= shar && shar <= '\u0B81')
      return "UNASSIGNED";
    else  if (shar == '\u0B82')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0B83')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0B84')
      return "UNASSIGNED";
    else  if ('\u0B85' <= shar && shar <= '\u0B8A')
      return "OTHER_LETTER";
    else  if ('\u0B8B' <= shar && shar <= '\u0B8D')
      return "UNASSIGNED";
    else  if ('\u0B8E' <= shar && shar <= '\u0B90')
      return "OTHER_LETTER";
    else  if (shar == '\u0B91')
      return "UNASSIGNED";
    else  if ('\u0B92' <= shar && shar <= '\u0B95')
      return "OTHER_LETTER";
    else  if ('\u0B96' <= shar && shar <= '\u0B98')
      return "UNASSIGNED";
    else  if ('\u0B99' <= shar && shar <= '\u0B9A')
      return "OTHER_LETTER";
    else  if (shar == '\u0B9B')
      return "UNASSIGNED";
    else  if (shar == '\u0B9C')
      return "OTHER_LETTER";
    else  if (shar == '\u0B9D')
      return "UNASSIGNED";
    else  if ('\u0B9E' <= shar && shar <= '\u0B9F')
      return "OTHER_LETTER";
    else  if ('\u0BA0' <= shar && shar <= '\u0BA2')
      return "UNASSIGNED";
    else  if ('\u0BA3' <= shar && shar <= '\u0BA4')
      return "OTHER_LETTER";
    else  if ('\u0BA5' <= shar && shar <= '\u0BA7')
      return "UNASSIGNED";
    else  if ('\u0BA8' <= shar && shar <= '\u0BAA')
      return "OTHER_LETTER";
    else  if ('\u0BAB' <= shar && shar <= '\u0BAD')
      return "UNASSIGNED";
    else  if ('\u0BAE' <= shar && shar <= '\u0BB5')
      return "OTHER_LETTER";
    else  if (shar == '\u0BB6')
      return "UNASSIGNED";
    else  if ('\u0BB7' <= shar && shar <= '\u0BB9')
      return "OTHER_LETTER";
    else  if ('\u0BBA' <= shar && shar <= '\u0BBD')
      return "UNASSIGNED";
    else  if ('\u0BBE' <= shar && shar <= '\u0BBF')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0BC0')
      return "NON_SPACING_MARK";
    else  if ('\u0BC1' <= shar && shar <= '\u0BC2')
      return "COMBINING_SPACING_MARK";
    else  if ('\u0BC3' <= shar && shar <= '\u0BC5')
      return "UNASSIGNED";
    else  if ('\u0BC6' <= shar && shar <= '\u0BC8')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0BC9')
      return "UNASSIGNED";
    else  if ('\u0BCA' <= shar && shar <= '\u0BCC')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0BCD')
      return "NON_SPACING_MARK";
    else  if ('\u0BCE' <= shar && shar <= '\u0BD6')
      return "UNASSIGNED";
    else  if (shar == '\u0BD7')
      return "COMBINING_SPACING_MARK";
    else  if ('\u0BD8' <= shar && shar <= '\u0BE6')
      return "UNASSIGNED";
    else  if ('\u0BE7' <= shar && shar <= '\u0BEF')
      return "DECIMAL_DIGIT_NUMBER";
    else  if ('\u0BF0' <= shar && shar <= '\u0BF2')
      return "OTHER_NUMBER";
    else  if ('\u0BF3' <= shar && shar <= '\u0C00')
      return "UNASSIGNED";
    else  if ('\u0C01' <= shar && shar <= '\u0C03')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0C04')
      return "UNASSIGNED";
    else  if ('\u0C05' <= shar && shar <= '\u0C0C')
      return "OTHER_LETTER";
    else  if (shar == '\u0C0D')
      return "UNASSIGNED";
    else  if ('\u0C0E' <= shar && shar <= '\u0C10')
      return "OTHER_LETTER";
    else  if (shar == '\u0C11')
      return "UNASSIGNED";
    else  if ('\u0C12' <= shar && shar <= '\u0C28')
      return "OTHER_LETTER";
    else  if (shar == '\u0C29')
      return "UNASSIGNED";
    else  if ('\u0C2A' <= shar && shar <= '\u0C33')
      return "OTHER_LETTER";
    else  if (shar == '\u0C34')
      return "UNASSIGNED";
    else  if ('\u0C35' <= shar && shar <= '\u0C39')
      return "OTHER_LETTER";
    else  if ('\u0C3A' <= shar && shar <= '\u0C3D')
      return "UNASSIGNED";
    else  if ('\u0C3E' <= shar && shar <= '\u0C40')
      return "NON_SPACING_MARK";
    else  if ('\u0C41' <= shar && shar <= '\u0C44')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0C45')
      return "UNASSIGNED";
    else  if ('\u0C46' <= shar && shar <= '\u0C48')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0C49')
      return "UNASSIGNED";
    else  if ('\u0C4A' <= shar && shar <= '\u0C4D')
      return "NON_SPACING_MARK";
    else  if ('\u0C4E' <= shar && shar <= '\u0C54')
      return "UNASSIGNED";
    else  if ('\u0C55' <= shar && shar <= '\u0C56')
      return "NON_SPACING_MARK";
    else  if ('\u0C57' <= shar && shar <= '\u0C5F')
      return "UNASSIGNED";
    else  if ('\u0C60' <= shar && shar <= '\u0C61')
      return "OTHER_LETTER";
    else  if ('\u0C62' <= shar && shar <= '\u0C65')
      return "UNASSIGNED";
    else  if ('\u0C66' <= shar && shar <= '\u0C6F')
      return "DECIMAL_DIGIT_NUMBER";
    else  if ('\u0C70' <= shar && shar <= '\u0C81')
      return "UNASSIGNED";
    else  if ('\u0C82' <= shar && shar <= '\u0C83')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0C84')
      return "UNASSIGNED";
    else  if ('\u0C85' <= shar && shar <= '\u0C8C')
      return "OTHER_LETTER";
    else  if (shar == '\u0C8D')
      return "UNASSIGNED";
    else  if ('\u0C8E' <= shar && shar <= '\u0C90')
      return "OTHER_LETTER";
    else  if (shar == '\u0C91')
      return "UNASSIGNED";
    else  if ('\u0C92' <= shar && shar <= '\u0CA8')
      return "OTHER_LETTER";
    else  if (shar == '\u0CA9')
      return "UNASSIGNED";
    else  if ('\u0CAA' <= shar && shar <= '\u0CB3')
      return "OTHER_LETTER";
    else  if (shar == '\u0CB4')
      return "UNASSIGNED";
    else  if ('\u0CB5' <= shar && shar <= '\u0CB9')
      return "OTHER_LETTER";
    else  if ('\u0CBA' <= shar && shar <= '\u0CBD')
      return "UNASSIGNED";
    else  if (shar == '\u0CBE')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0CBF')
      return "NON_SPACING_MARK";
    else  if ('\u0CC0' <= shar && shar <= '\u0CC4')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0CC5')
      return "UNASSIGNED";
    else  if (shar == '\u0CC6')
      return "NON_SPACING_MARK";
    else  if ('\u0CC7' <= shar && shar <= '\u0CC8')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0CC9')
      return "UNASSIGNED";
    else  if ('\u0CCA' <= shar && shar <= '\u0CCB')
      return "COMBINING_SPACING_MARK";
    else  if ('\u0CCC' <= shar && shar <= '\u0CCD')
      return "NON_SPACING_MARK";
    else  if ('\u0CCE' <= shar && shar <= '\u0CD4')
      return "UNASSIGNED";
    else  if ('\u0CD5' <= shar && shar <= '\u0CD6')
      return "COMBINING_SPACING_MARK";
    else  if ('\u0CD7' <= shar && shar <= '\u0CDD')
      return "UNASSIGNED";
    else  if (shar == '\u0CDE')
      return "OTHER_LETTER";
    else  if (shar == '\u0CDF')
      return "UNASSIGNED";
    else  if ('\u0CE0' <= shar && shar <= '\u0CE1')
      return "OTHER_LETTER";
    else  if ('\u0CE2' <= shar && shar <= '\u0CE5')
      return "UNASSIGNED";
    else  if ('\u0CE6' <= shar && shar <= '\u0CEF')
      return "DECIMAL_DIGIT_NUMBER";
    else  if ('\u0CF0' <= shar && shar <= '\u0D01')
      return "UNASSIGNED";
    else  if ('\u0D02' <= shar && shar <= '\u0D03')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0D04')
      return "UNASSIGNED";
    else  if ('\u0D05' <= shar && shar <= '\u0D0C')
      return "OTHER_LETTER";
    else  if (shar == '\u0D0D')
      return "UNASSIGNED";
    else  if ('\u0D0E' <= shar && shar <= '\u0D10')
      return "OTHER_LETTER";
    else  if (shar == '\u0D11')
      return "UNASSIGNED";
    else  if ('\u0D12' <= shar && shar <= '\u0D28')
      return "OTHER_LETTER";
    else  if (shar == '\u0D29')
      return "UNASSIGNED";
    else  if ('\u0D2A' <= shar && shar <= '\u0D39')
      return "OTHER_LETTER";
    else  if ('\u0D3A' <= shar && shar <= '\u0D3D')
      return "UNASSIGNED";
    else  if ('\u0D3E' <= shar && shar <= '\u0D40')
      return "COMBINING_SPACING_MARK";
    else  if ('\u0D41' <= shar && shar <= '\u0D43')
      return "NON_SPACING_MARK";
    else  if ('\u0D44' <= shar && shar <= '\u0D45')
      return "UNASSIGNED";
    else  if ('\u0D46' <= shar && shar <= '\u0D48')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0D49')
      return "UNASSIGNED";
    else  if ('\u0D4A' <= shar && shar <= '\u0D4C')
      return "COMBINING_SPACING_MARK";
    else  if (shar == '\u0D4D')
      return "NON_SPACING_MARK";
    else  if ('\u0D4E' <= shar && shar <= '\u0D56')
      return "UNASSIGNED";
    else  if (shar == '\u0D57')
      return "COMBINING_SPACING_MARK";
    else  if ('\u0D58' <= shar && shar <= '\u0D5F')
      return "UNASSIGNED";
    else  if ('\u0D60' <= shar && shar <= '\u0D61')
      return "OTHER_LETTER";
    else  if ('\u0D62' <= shar && shar <= '\u0D65')
      return "UNASSIGNED";
    else  if ('\u0D66' <= shar && shar <= '\u0D6F')
      return "DECIMAL_DIGIT_NUMBER";
    else  if ('\u0D70' <= shar && shar <= '\u0E00')
      return "UNASSIGNED";
    else  if ('\u0E01' <= shar && shar <= '\u0E2E')
      return "OTHER_LETTER";
    else  if (shar == '\u0E2F')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u0E30')
      return "OTHER_LETTER";
    else  if (shar == '\u0E31')
      return "NON_SPACING_MARK";
    else  if ('\u0E32' <= shar && shar <= '\u0E33')
      return "OTHER_LETTER";
    else  if ('\u0E34' <= shar && shar <= '\u0E3A')
      return "NON_SPACING_MARK";
    else  if ('\u0E3B' <= shar && shar <= '\u0E3E')
      return "UNASSIGNED";
    else  if (shar == '\u0E3F')
      return "CURRENCY_SYMBOL";
    else  if ('\u0E40' <= shar && shar <= '\u0E45')
      return "OTHER_LETTER";
    else  if (shar == '\u0E46')
      return "MODIFIER_LETTER";
    else  if ('\u0E47' <= shar && shar <= '\u0E4E')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0E4F')
      return "OTHER_SYMBOL";
    else  if ('\u0E50' <= shar && shar <= '\u0E59')
      return "DECIMAL_DIGIT_NUMBER";
    else  if ('\u0E5A' <= shar && shar <= '\u0E5B')
      return "OTHER_PUNCTUATION";
    else  if ('\u0E5C' <= shar && shar <= '\u0E80')
      return "UNASSIGNED";
    else  if ('\u0E81' <= shar && shar <= '\u0E82')
      return "OTHER_LETTER";
    else  if (shar == '\u0E83')
      return "UNASSIGNED";
    else  if (shar == '\u0E84')
      return "OTHER_LETTER";
    else  if ('\u0E85' <= shar && shar <= '\u0E86')
      return "UNASSIGNED";
    else  if ('\u0E87' <= shar && shar <= '\u0E88')
      return "OTHER_LETTER";
    else  if (shar == '\u0E89')
      return "UNASSIGNED";
    else  if (shar == '\u0E8A')
      return "OTHER_LETTER";
    else  if ('\u0E8B' <= shar && shar <= '\u0E8C')
      return "UNASSIGNED";
    else  if (shar == '\u0E8D')
      return "OTHER_LETTER";
    else  if ('\u0E8E' <= shar && shar <= '\u0E93')
      return "UNASSIGNED";
    else  if ('\u0E94' <= shar && shar <= '\u0E97')
      return "OTHER_LETTER";
    else  if (shar == '\u0E98')
      return "UNASSIGNED";
    else  if ('\u0E99' <= shar && shar <= '\u0E9F')
      return "OTHER_LETTER";
    else  if (shar == '\u0EA0')
      return "UNASSIGNED";
    else  if ('\u0EA1' <= shar && shar <= '\u0EA3')
      return "OTHER_LETTER";
    else  if (shar == '\u0EA4')
      return "UNASSIGNED";
    else  if (shar == '\u0EA5')
      return "OTHER_LETTER";
    else  if (shar == '\u0EA6')
      return "UNASSIGNED";
    else  if (shar == '\u0EA7')
      return "OTHER_LETTER";
    else  if ('\u0EA8' <= shar && shar <= '\u0EA9')
      return "UNASSIGNED";
    else  if ('\u0EAA' <= shar && shar <= '\u0EAB')
      return "OTHER_LETTER";
    else  if (shar == '\u0EAC')
      return "UNASSIGNED";
    else  if ('\u0EAD' <= shar && shar <= '\u0EAE')
      return "OTHER_LETTER";
    else  if (shar == '\u0EAF')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u0EB0')
      return "OTHER_LETTER";
    else  if (shar == '\u0EB1')
      return "NON_SPACING_MARK";
    else  if ('\u0EB2' <= shar && shar <= '\u0EB3')
      return "OTHER_LETTER";
    else  if ('\u0EB4' <= shar && shar <= '\u0EB9')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0EBA')
      return "UNASSIGNED";
    else  if ('\u0EBB' <= shar && shar <= '\u0EBC')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0EBD')
      return "OTHER_LETTER";
    else  if ('\u0EBE' <= shar && shar <= '\u0EBF')
      return "UNASSIGNED";
    else  if ('\u0EC0' <= shar && shar <= '\u0EC4')
      return "OTHER_LETTER";
    else  if (shar == '\u0EC5')
      return "UNASSIGNED";
    else  if (shar == '\u0EC6')
      return "MODIFIER_LETTER";
    else  if (shar == '\u0EC7')
      return "UNASSIGNED";
    else  if ('\u0EC8' <= shar && shar <= '\u0ECD')
      return "NON_SPACING_MARK";
    else  if ('\u0ECE' <= shar && shar <= '\u0ECF')
      return "UNASSIGNED";
    else  if ('\u0ED0' <= shar && shar <= '\u0ED9')
      return "DECIMAL_DIGIT_NUMBER";
    else  if ('\u0EDA' <= shar && shar <= '\u0EDB')
      return "UNASSIGNED";
    else  if ('\u0EDC' <= shar && shar <= '\u0EDD')
      return "OTHER_LETTER";
    else  if ('\u0EDE' <= shar && shar <= '\u0EFF')
      return "UNASSIGNED";
    else  if ('\u0F00' <= shar && shar <= '\u0F03')
      return "OTHER_SYMBOL";
    else  if ('\u0F04' <= shar && shar <= '\u0F12')
      return "OTHER_PUNCTUATION";
    else  if ('\u0F13' <= shar && shar <= '\u0F17')
      return "OTHER_SYMBOL";
    else  if ('\u0F18' <= shar && shar <= '\u0F19')
      return "NON_SPACING_MARK";
    else  if ('\u0F1A' <= shar && shar <= '\u0F1F')
      return "OTHER_SYMBOL";
    else  if ('\u0F20' <= shar && shar <= '\u0F29')
      return "DECIMAL_DIGIT_NUMBER";
    else  if ('\u0F2A' <= shar && shar <= '\u0F33')
      return "OTHER_NUMBER";
    else  if (shar == '\u0F34')
      return "OTHER_SYMBOL";
    else  if (shar == '\u0F35')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0F36')
      return "OTHER_SYMBOL";
    else  if (shar == '\u0F37')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0F38')
      return "OTHER_SYMBOL";
    else  if (shar == '\u0F39')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0F3A')
      return "START_PUNCTUATION";
    else  if (shar == '\u0F3B')
      return "END_PUNCTUATION";
    else  if (shar == '\u0F3C')
      return "START_PUNCTUATION";
    else  if (shar == '\u0F3D')
      return "END_PUNCTUATION";
    else  if ('\u0F3E' <= shar && shar <= '\u0F3F')
      return "COMBINING_SPACING_MARK";
    else  if ('\u0F40' <= shar && shar <= '\u0F47')
      return "OTHER_LETTER";
    else  if (shar == '\u0F48')
      return "UNASSIGNED";
    else  if ('\u0F49' <= shar && shar <= '\u0F69')
      return "OTHER_LETTER";
    else  if ('\u0F6A' <= shar && shar <= '\u0F70')
      return "UNASSIGNED";
    else  if ('\u0F71' <= shar && shar <= '\u0F7E')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0F7F')
      return "COMBINING_SPACING_MARK";
    else  if ('\u0F80' <= shar && shar <= '\u0F84')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0F85')
      return "OTHER_PUNCTUATION";
    else  if ('\u0F86' <= shar && shar <= '\u0F8B')
      return "NON_SPACING_MARK";
    else  if ('\u0F8C' <= shar && shar <= '\u0F8F')
      return "UNASSIGNED";
    else  if ('\u0F90' <= shar && shar <= '\u0F95')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0F96')
      return "UNASSIGNED";
    else  if (shar == '\u0F97')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0F98')
      return "UNASSIGNED";
    else  if ('\u0F99' <= shar && shar <= '\u0FAD')
      return "NON_SPACING_MARK";
    else  if ('\u0FAE' <= shar && shar <= '\u0FB0')
      return "UNASSIGNED";
    else  if ('\u0FB1' <= shar && shar <= '\u0FB7')
      return "NON_SPACING_MARK";
    else  if (shar == '\u0FB8')
      return "UNASSIGNED";
    else  if (shar == '\u0FB9')
      return "NON_SPACING_MARK";
    else  if ('\u0FBA' <= shar && shar <= '\u109F')
      return "UNASSIGNED";
    else  if ('\u10A0' <= shar && shar <= '\u10C5')
      return "UPPERCASE_LETTER";
    else  if ('\u10C6' <= shar && shar <= '\u10CF')
      return "UNASSIGNED";
    else  if ('\u10D0' <= shar && shar <= '\u10F6')
      return "LOWERCASE_LETTER";
    else  if ('\u10F7' <= shar && shar <= '\u10FA')
      return "UNASSIGNED";
    else  if (shar == '\u10FB')
      return "OTHER_PUNCTUATION";
    else  if ('\u10FC' <= shar && shar <= '\u10FF')
      return "UNASSIGNED";
    else  if ('\u1100' <= shar && shar <= '\u1159')
      return "OTHER_LETTER";
    else  if ('\u115A' <= shar && shar <= '\u115E')
      return "UNASSIGNED";
    else  if ('\u115F' <= shar && shar <= '\u11A2')
      return "OTHER_LETTER";
    else  if ('\u11A3' <= shar && shar <= '\u11A7')
      return "UNASSIGNED";
    else  if ('\u11A8' <= shar && shar <= '\u11F9')
      return "OTHER_LETTER";
    else  if ('\u11FA' <= shar && shar <= '\u1DFF')
      return "UNASSIGNED";
    else  if (shar == '\u1E00')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E01')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E02')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E03')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E04')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E05')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E06')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E07')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E08')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E09')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E0A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E0B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E0C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E0D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E0E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E0F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E10')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E11')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E12')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E13')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E14')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E15')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E16')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E17')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E18')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E19')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E1A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E1B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E1C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E1D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E1E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E1F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E20')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E21')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E22')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E23')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E24')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E25')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E26')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E27')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E28')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E29')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E2A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E2B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E2C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E2D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E2E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E2F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E30')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E31')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E32')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E33')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E34')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E35')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E36')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E37')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E38')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E39')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E3A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E3B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E3C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E3D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E3E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E3F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E40')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E41')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E42')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E43')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E44')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E45')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E46')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E47')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E48')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E49')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E4A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E4B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E4C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E4D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E4E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E4F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E50')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E51')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E52')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E53')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E54')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E55')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E56')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E57')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E58')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E59')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E5A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E5B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E5C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E5D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E5E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E5F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E60')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E61')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E62')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E63')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E64')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E65')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E66')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E67')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E68')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E69')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E6A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E6B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E6C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E6D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E6E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E6F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E70')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E71')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E72')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E73')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E74')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E75')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E76')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E77')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E78')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E79')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E7A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E7B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E7C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E7D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E7E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E7F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E80')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E81')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E82')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E83')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E84')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E85')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E86')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E87')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E88')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E89')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E8A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E8B')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E8C')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E8D')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E8E')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E8F')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E90')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E91')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E92')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1E93')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1E94')
      return "UPPERCASE_LETTER";
    else  if ('\u1E95' <= shar && shar <= '\u1E9B')
      return "LOWERCASE_LETTER";
    else  if ('\u1E9C' <= shar && shar <= '\u1E9F')
      return "UNASSIGNED";
    else  if (shar == '\u1EA0')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EA1')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EA2')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EA3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EA4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EA5')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EA6')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EA7')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EA8')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EA9')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EAA')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EAB')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EAC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EAD')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EAE')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EAF')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EB0')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EB1')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EB2')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EB3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EB4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EB5')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EB6')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EB7')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EB8')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EB9')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EBA')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EBB')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EBC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EBD')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EBE')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EBF')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EC0')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EC1')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EC2')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EC3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EC4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EC5')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EC6')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EC7')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EC8')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EC9')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1ECA')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1ECB')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1ECC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1ECD')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1ECE')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1ECF')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1ED0')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1ED1')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1ED2')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1ED3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1ED4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1ED5')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1ED6')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1ED7')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1ED8')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1ED9')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EDA')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EDB')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EDC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EDD')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EDE')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EDF')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EE0')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EE1')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EE2')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EE3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EE4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EE5')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EE6')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EE7')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EE8')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EE9')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EEA')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EEB')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EEC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EED')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EEE')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EEF')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EF0')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EF1')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EF2')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EF3')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EF4')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EF5')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EF6')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EF7')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1EF8')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1EF9')
      return "LOWERCASE_LETTER";
    else  if ('\u1EFA' <= shar && shar <= '\u1EFF')
      return "UNASSIGNED";
    else  if ('\u1F00' <= shar && shar <= '\u1F07')
      return "LOWERCASE_LETTER";
    else  if ('\u1F08' <= shar && shar <= '\u1F0F')
      return "UPPERCASE_LETTER";
    else  if ('\u1F10' <= shar && shar <= '\u1F15')
      return "LOWERCASE_LETTER";
    else  if ('\u1F16' <= shar && shar <= '\u1F17')
      return "UNASSIGNED";
    else  if ('\u1F18' <= shar && shar <= '\u1F1D')
      return "UPPERCASE_LETTER";
    else  if ('\u1F1E' <= shar && shar <= '\u1F1F')
      return "UNASSIGNED";
    else  if ('\u1F20' <= shar && shar <= '\u1F27')
      return "LOWERCASE_LETTER";
    else  if ('\u1F28' <= shar && shar <= '\u1F2F')
      return "UPPERCASE_LETTER";
    else  if ('\u1F30' <= shar && shar <= '\u1F37')
      return "LOWERCASE_LETTER";
    else  if ('\u1F38' <= shar && shar <= '\u1F3F')
      return "UPPERCASE_LETTER";
    else  if ('\u1F40' <= shar && shar <= '\u1F45')
      return "LOWERCASE_LETTER";
    else  if ('\u1F46' <= shar && shar <= '\u1F47')
      return "UNASSIGNED";
    else  if ('\u1F48' <= shar && shar <= '\u1F4D')
      return "UPPERCASE_LETTER";
    else  if ('\u1F4E' <= shar && shar <= '\u1F4F')
      return "UNASSIGNED";
    else  if ('\u1F50' <= shar && shar <= '\u1F57')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1F58')
      return "UNASSIGNED";
    else  if (shar == '\u1F59')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1F5A')
      return "UNASSIGNED";
    else  if (shar == '\u1F5B')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1F5C')
      return "UNASSIGNED";
    else  if (shar == '\u1F5D')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1F5E')
      return "UNASSIGNED";
    else  if (shar == '\u1F5F')
      return "UPPERCASE_LETTER";
    else  if ('\u1F60' <= shar && shar <= '\u1F67')
      return "LOWERCASE_LETTER";
    else  if ('\u1F68' <= shar && shar <= '\u1F6F')
      return "UPPERCASE_LETTER";
    else  if ('\u1F70' <= shar && shar <= '\u1F7D')
      return "LOWERCASE_LETTER";
    else  if ('\u1F7E' <= shar && shar <= '\u1F7F')
      return "UNASSIGNED";
    else  if ('\u1F80' <= shar && shar <= '\u1F87')
      return "LOWERCASE_LETTER";
    else  if ('\u1F88' <= shar && shar <= '\u1F8F')
      return "UPPERCASE_LETTER";
    else  if ('\u1F90' <= shar && shar <= '\u1F97')
      return "LOWERCASE_LETTER";
    else  if ('\u1F98' <= shar && shar <= '\u1F9F')
      return "UPPERCASE_LETTER";
    else  if ('\u1FA0' <= shar && shar <= '\u1FA7')
      return "LOWERCASE_LETTER";
    else  if ('\u1FA8' <= shar && shar <= '\u1FAF')
      return "UPPERCASE_LETTER";
    else  if ('\u1FB0' <= shar && shar <= '\u1FB4')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1FB5')
      return "UNASSIGNED";
    else  if ('\u1FB6' <= shar && shar <= '\u1FB7')
      return "LOWERCASE_LETTER";
    else  if ('\u1FB8' <= shar && shar <= '\u1FBC')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1FBD')
      return "MODIFIER_SYMBOL";
    else  if (shar == '\u1FBE')
      return "UPPERCASE_LETTER";
    else  if ('\u1FBF' <= shar && shar <= '\u1FC1')
      return "MODIFIER_SYMBOL";
    else  if ('\u1FC2' <= shar && shar <= '\u1FC4')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1FC5')
      return "UNASSIGNED";
    else  if ('\u1FC6' <= shar && shar <= '\u1FC7')
      return "LOWERCASE_LETTER";
    else  if ('\u1FC8' <= shar && shar <= '\u1FCC')
      return "UPPERCASE_LETTER";
    else  if ('\u1FCD' <= shar && shar <= '\u1FCF')
      return "MODIFIER_SYMBOL";
    else  if ('\u1FD0' <= shar && shar <= '\u1FD3')
      return "LOWERCASE_LETTER";
    else  if ('\u1FD4' <= shar && shar <= '\u1FD5')
      return "UNASSIGNED";
    else  if ('\u1FD6' <= shar && shar <= '\u1FD7')
      return "LOWERCASE_LETTER";
    else  if ('\u1FD8' <= shar && shar <= '\u1FDB')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u1FDC')
      return "UNASSIGNED";
    else  if ('\u1FDD' <= shar && shar <= '\u1FDF')
      return "MODIFIER_SYMBOL";
    else  if ('\u1FE0' <= shar && shar <= '\u1FE7')
      return "LOWERCASE_LETTER";
    else  if ('\u1FE8' <= shar && shar <= '\u1FEC')
      return "UPPERCASE_LETTER";
    else  if ('\u1FED' <= shar && shar <= '\u1FEF')
      return "MODIFIER_SYMBOL";
    else  if ('\u1FF0' <= shar && shar <= '\u1FF1')
      return "UNASSIGNED";
    else  if ('\u1FF2' <= shar && shar <= '\u1FF4')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u1FF5')
      return "UNASSIGNED";
    else  if ('\u1FF6' <= shar && shar <= '\u1FF7')
      return "LOWERCASE_LETTER";
    else  if ('\u1FF8' <= shar && shar <= '\u1FFC')
      return "UPPERCASE_LETTER";
    else  if ('\u1FFD' <= shar && shar <= '\u1FFE')
      return "MODIFIER_SYMBOL";
    else  if (shar == '\u1FFF')
      return "UNASSIGNED";
    else  if ('\u2000' <= shar && shar <= '\u200B')
      return "SPACE_SEPARATOR";
    else  if ('\u200C' <= shar && shar <= '\u200F')
      return "FORMAT";
    else  if ('\u2010' <= shar && shar <= '\u2015')
      return "DASH_PUNCTUATION";
    else  if ('\u2016' <= shar && shar <= '\u2017')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u2018')
      return "START_PUNCTUATION";
    else  if (shar == '\u2019')
      return "END_PUNCTUATION";
    else  if ('\u201A' <= shar && shar <= '\u201C')
      return "START_PUNCTUATION";
    else  if (shar == '\u201D')
      return "END_PUNCTUATION";
    else  if ('\u201E' <= shar && shar <= '\u201F')
      return "START_PUNCTUATION";
    else  if ('\u2020' <= shar && shar <= '\u2027')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u2028')
      return "LINE_SEPARATOR";
    else  if (shar == '\u2029')
      return "PARAGRAPH_SEPARATOR";
    else  if ('\u202A' <= shar && shar <= '\u202E')
      return "FORMAT";
    else  if (shar == '\u202F')
      return "UNASSIGNED";
    else  if ('\u2030' <= shar && shar <= '\u2038')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u2039')
      return "START_PUNCTUATION";
    else  if (shar == '\u203A')
      return "END_PUNCTUATION";
    else  if ('\u203B' <= shar && shar <= '\u203E')
      return "OTHER_PUNCTUATION";
    else  if ('\u203F' <= shar && shar <= '\u2040')
      return "CONNECTOR_PUNCTUATION";
    else  if ('\u2041' <= shar && shar <= '\u2043')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u2044')
      return "MATH_SYMBOL";
    else  if (shar == '\u2045')
      return "START_PUNCTUATION";
    else  if (shar == '\u2046')
      return "END_PUNCTUATION";
    else  if ('\u2047' <= shar && shar <= '\u2069')
      return "UNASSIGNED";
    else  if ('\u206A' <= shar && shar <= '\u206F')
      return "FORMAT";
    else  if (shar == '\u2070')
      return "OTHER_NUMBER";
    else  if ('\u2071' <= shar && shar <= '\u2073')
      return "UNASSIGNED";
    else  if ('\u2074' <= shar && shar <= '\u2079')
      return "OTHER_NUMBER";
    else  if ('\u207A' <= shar && shar <= '\u207C')
      return "MATH_SYMBOL";
    else  if (shar == '\u207D')
      return "START_PUNCTUATION";
    else  if (shar == '\u207E')
      return "END_PUNCTUATION";
    else  if (shar == '\u207F')
      return "LOWERCASE_LETTER";
    else  if ('\u2080' <= shar && shar <= '\u2089')
      return "OTHER_NUMBER";
    else  if ('\u208A' <= shar && shar <= '\u208C')
      return "MATH_SYMBOL";
    else  if (shar == '\u208D')
      return "START_PUNCTUATION";
    else  if (shar == '\u208E')
      return "END_PUNCTUATION";
    else  if ('\u208F' <= shar && shar <= '\u209F')
      return "UNASSIGNED";
    else  if ('\u20A0' <= shar && shar <= '\u20AC')
      return "CURRENCY_SYMBOL";
    else  if ('\u20AD' <= shar && shar <= '\u20CF')
      return "UNASSIGNED";
    else  if ('\u20D0' <= shar && shar <= '\u20DC')
      return "NON_SPACING_MARK";
    else  if ('\u20DD' <= shar && shar <= '\u20E0')
      return "ENCLOSING_MARK";
    else  if (shar == '\u20E1')
      return "NON_SPACING_MARK";
    else  if ('\u20E2' <= shar && shar <= '\u20FF')
      return "UNASSIGNED";
    else  if ('\u2100' <= shar && shar <= '\u2101')
      return "OTHER_SYMBOL";
    else  if (shar == '\u2102')
      return "UPPERCASE_LETTER";
    else  if ('\u2103' <= shar && shar <= '\u2106')
      return "OTHER_SYMBOL";
    else  if (shar == '\u2107')
      return "UPPERCASE_LETTER";
    else  if ('\u2108' <= shar && shar <= '\u2109')
      return "OTHER_SYMBOL";
    else  if (shar == '\u210A')
      return "LOWERCASE_LETTER";
    else  if ('\u210B' <= shar && shar <= '\u210D')
      return "UPPERCASE_LETTER";
    else  if ('\u210E' <= shar && shar <= '\u210F')
      return "LOWERCASE_LETTER";
    else  if ('\u2110' <= shar && shar <= '\u2112')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u2113')
      return "LOWERCASE_LETTER";
    else  if (shar == '\u2114')
      return "OTHER_SYMBOL";
    else  if (shar == '\u2115')
      return "UPPERCASE_LETTER";
    else  if ('\u2116' <= shar && shar <= '\u2117')
      return "OTHER_SYMBOL";
    else  if ('\u2118' <= shar && shar <= '\u211D')
      return "UPPERCASE_LETTER";
    else  if ('\u211E' <= shar && shar <= '\u2123')
      return "OTHER_SYMBOL";
    else  if (shar == '\u2124')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u2125')
      return "OTHER_SYMBOL";
    else  if (shar == '\u2126')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u2127')
      return "OTHER_SYMBOL";
    else  if (shar == '\u2128')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u2129')
      return "OTHER_SYMBOL";
    else  if ('\u212A' <= shar && shar <= '\u212D')
      return "UPPERCASE_LETTER";
    else  if ('\u212E' <= shar && shar <= '\u212F')
      return "LOWERCASE_LETTER";
    else  if ('\u2130' <= shar && shar <= '\u2131')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u2132')
      return "OTHER_SYMBOL";
    else  if (shar == '\u2133')
      return "UPPERCASE_LETTER";
    else  if (shar == '\u2134')
      return "LOWERCASE_LETTER";
    else  if ('\u2135' <= shar && shar <= '\u2138')
      return "OTHER_LETTER";
    else  if ('\u2139' <= shar && shar <= '\u2152')
      return "UNASSIGNED";
    else  if ('\u2153' <= shar && shar <= '\u215F')
      return "OTHER_NUMBER";
    else  if ('\u2160' <= shar && shar <= '\u2182')
      return "LETTER_NUMBER";
    else  if ('\u2183' <= shar && shar <= '\u218F')
      return "UNASSIGNED";
    else  if ('\u2190' <= shar && shar <= '\u2194')
      return "MATH_SYMBOL";
    else  if ('\u2195' <= shar && shar <= '\u21D1')
      return "OTHER_SYMBOL";
    else  if (shar == '\u21D2')
      return "MATH_SYMBOL";
    else  if (shar == '\u21D3')
      return "OTHER_SYMBOL";
    else  if (shar == '\u21D4')
      return "MATH_SYMBOL";
    else  if ('\u21D5' <= shar && shar <= '\u21EA')
      return "OTHER_SYMBOL";
    else  if ('\u21EB' <= shar && shar <= '\u21FF')
      return "UNASSIGNED";
    else  if ('\u2200' <= shar && shar <= '\u22F1')
      return "MATH_SYMBOL";
    else  if ('\u22F2' <= shar && shar <= '\u22FF')
      return "UNASSIGNED";
    else  if (shar == '\u2300')
      return "OTHER_SYMBOL";
    else  if (shar == '\u2301')
      return "UNASSIGNED";
    else  if ('\u2302' <= shar && shar <= '\u2307')
      return "OTHER_SYMBOL";
    else  if ('\u2308' <= shar && shar <= '\u230B')
      return "MATH_SYMBOL";
    else  if ('\u230C' <= shar && shar <= '\u231F')
      return "OTHER_SYMBOL";
    else  if ('\u2320' <= shar && shar <= '\u2321')
      return "MATH_SYMBOL";
    else  if ('\u2322' <= shar && shar <= '\u2328')
      return "OTHER_SYMBOL";
    else  if (shar == '\u2329')
      return "START_PUNCTUATION";
    else  if (shar == '\u232A')
      return "END_PUNCTUATION";
    else  if ('\u232B' <= shar && shar <= '\u237A')
      return "OTHER_SYMBOL";
    else  if ('\u237B' <= shar && shar <= '\u23FF')
      return "UNASSIGNED";
    else  if ('\u2400' <= shar && shar <= '\u2424')
      return "OTHER_SYMBOL";
    else  if ('\u2425' <= shar && shar <= '\u243F')
      return "UNASSIGNED";
    else  if ('\u2440' <= shar && shar <= '\u244A')
      return "OTHER_SYMBOL";
    else  if ('\u244B' <= shar && shar <= '\u245F')
      return "UNASSIGNED";
    else  if ('\u2460' <= shar && shar <= '\u249B')
      return "OTHER_NUMBER";
    else  if ('\u249C' <= shar && shar <= '\u24E9')
      return "OTHER_SYMBOL";
    else  if (shar == '\u24EA')
      return "OTHER_NUMBER";
    else  if ('\u24EB' <= shar && shar <= '\u24FF')
      return "UNASSIGNED";
    else  if ('\u2500' <= shar && shar <= '\u2595')
      return "OTHER_SYMBOL";
    else  if ('\u2596' <= shar && shar <= '\u259F')
      return "UNASSIGNED";
    else  if ('\u25A0' <= shar && shar <= '\u25EF')
      return "OTHER_SYMBOL";
    else  if ('\u25F0' <= shar && shar <= '\u25FF')
      return "UNASSIGNED";
    else  if ('\u2600' <= shar && shar <= '\u2613')
      return "OTHER_SYMBOL";
    else  if ('\u2614' <= shar && shar <= '\u2619')
      return "UNASSIGNED";
    else  if ('\u261A' <= shar && shar <= '\u266F')
      return "OTHER_SYMBOL";
    else  if ('\u2670' <= shar && shar <= '\u2700')
      return "UNASSIGNED";
    else  if ('\u2701' <= shar && shar <= '\u2704')
      return "OTHER_SYMBOL";
    else  if (shar == '\u2705')
      return "UNASSIGNED";
    else  if ('\u2706' <= shar && shar <= '\u2709')
      return "OTHER_SYMBOL";
    else  if ('\u270A' <= shar && shar <= '\u270B')
      return "UNASSIGNED";
    else  if ('\u270C' <= shar && shar <= '\u2727')
      return "OTHER_SYMBOL";
    else  if (shar == '\u2728')
      return "UNASSIGNED";
    else  if ('\u2729' <= shar && shar <= '\u274B')
      return "OTHER_SYMBOL";
    else  if (shar == '\u274C')
      return "UNASSIGNED";
    else  if (shar == '\u274D')
      return "OTHER_SYMBOL";
    else  if (shar == '\u274E')
      return "UNASSIGNED";
    else  if ('\u274F' <= shar && shar <= '\u2752')
      return "OTHER_SYMBOL";
    else  if ('\u2753' <= shar && shar <= '\u2755')
      return "UNASSIGNED";
    else  if (shar == '\u2756')
      return "OTHER_SYMBOL";
    else  if (shar == '\u2757')
      return "UNASSIGNED";
    else  if ('\u2758' <= shar && shar <= '\u275E')
      return "OTHER_SYMBOL";
    else  if ('\u275F' <= shar && shar <= '\u2760')
      return "UNASSIGNED";
    else  if ('\u2761' <= shar && shar <= '\u2767')
      return "OTHER_SYMBOL";
    else  if ('\u2768' <= shar && shar <= '\u2775')
      return "UNASSIGNED";
    else  if ('\u2776' <= shar && shar <= '\u2793')
      return "OTHER_NUMBER";
    else  if (shar == '\u2794')
      return "OTHER_SYMBOL";
    else  if ('\u2795' <= shar && shar <= '\u2797')
      return "UNASSIGNED";
    else  if ('\u2798' <= shar && shar <= '\u27AF')
      return "OTHER_SYMBOL";
    else  if (shar == '\u27B0')
      return "UNASSIGNED";
    else  if ('\u27B1' <= shar && shar <= '\u27BE')
      return "OTHER_SYMBOL";
    else  if ('\u27BF' <= shar && shar <= '\u2FFF')
      return "UNASSIGNED";
    else  if (shar == '\u3000')
      return "SPACE_SEPARATOR";
    else  if ('\u3001' <= shar && shar <= '\u3003')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u3004')
      return "OTHER_SYMBOL";
    else  if (shar == '\u3005')
      return "MODIFIER_LETTER";
    else  if (shar == '\u3006')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\u3007')
      return "LETTER_NUMBER";
    else  if (shar == '\u3008')
      return "START_PUNCTUATION";
    else  if (shar == '\u3009')
      return "END_PUNCTUATION";
    else  if (shar == '\u300A')
      return "START_PUNCTUATION";
    else  if (shar == '\u300B')
      return "END_PUNCTUATION";
    else  if (shar == '\u300C')
      return "START_PUNCTUATION";
    else  if (shar == '\u300D')
      return "END_PUNCTUATION";
    else  if (shar == '\u300E')
      return "START_PUNCTUATION";
    else  if (shar == '\u300F')
      return "END_PUNCTUATION";
    else  if (shar == '\u3010')
      return "START_PUNCTUATION";
    else  if (shar == '\u3011')
      return "END_PUNCTUATION";
    else  if ('\u3012' <= shar && shar <= '\u3013')
      return "OTHER_SYMBOL";
    else  if (shar == '\u3014')
      return "START_PUNCTUATION";
    else  if (shar == '\u3015')
      return "END_PUNCTUATION";
    else  if (shar == '\u3016')
      return "START_PUNCTUATION";
    else  if (shar == '\u3017')
      return "END_PUNCTUATION";
    else  if (shar == '\u3018')
      return "START_PUNCTUATION";
    else  if (shar == '\u3019')
      return "END_PUNCTUATION";
    else  if (shar == '\u301A')
      return "START_PUNCTUATION";
    else  if (shar == '\u301B')
      return "END_PUNCTUATION";
    else  if (shar == '\u301C')
      return "DASH_PUNCTUATION";
    else  if (shar == '\u301D')
      return "START_PUNCTUATION";
    else  if ('\u301E' <= shar && shar <= '\u301F')
      return "END_PUNCTUATION";
    else  if (shar == '\u3020')
      return "OTHER_SYMBOL";
    else  if ('\u3021' <= shar && shar <= '\u3029')
      return "LETTER_NUMBER";
    else  if ('\u302A' <= shar && shar <= '\u302F')
      return "NON_SPACING_MARK";
    else  if (shar == '\u3030')
      return "DASH_PUNCTUATION";
    else  if ('\u3031' <= shar && shar <= '\u3035')
      return "MODIFIER_LETTER";
    else  if ('\u3036' <= shar && shar <= '\u3037')
      return "OTHER_SYMBOL";
    else  if ('\u3038' <= shar && shar <= '\u303E')
      return "UNASSIGNED";
    else  if (shar == '\u303F')
      return "OTHER_SYMBOL";
    else  if (shar == '\u3040')
      return "UNASSIGNED";
    else  if ('\u3041' <= shar && shar <= '\u3094')
      return "OTHER_LETTER";
    else  if ('\u3095' <= shar && shar <= '\u3098')
      return "UNASSIGNED";
    else  if ('\u3099' <= shar && shar <= '\u309A')
      return "NON_SPACING_MARK";
    else  if ('\u309B' <= shar && shar <= '\u309E')
      return "MODIFIER_LETTER";
    else  if ('\u309F' <= shar && shar <= '\u30A0')
      return "UNASSIGNED";
    else  if ('\u30A1' <= shar && shar <= '\u30FA')
      return "OTHER_LETTER";
    else  if (shar == '\u30FB')
      return "OTHER_PUNCTUATION";
    else  if ('\u30FC' <= shar && shar <= '\u30FE')
      return "MODIFIER_LETTER";
    else  if ('\u30FF' <= shar && shar <= '\u3104')
      return "UNASSIGNED";
    else  if ('\u3105' <= shar && shar <= '\u312C')
      return "OTHER_LETTER";
    else  if ('\u312D' <= shar && shar <= '\u3130')
      return "UNASSIGNED";
    else  if ('\u3131' <= shar && shar <= '\u318E')
      return "OTHER_LETTER";
    else  if (shar == '\u318F')
      return "UNASSIGNED";
    else  if ('\u3190' <= shar && shar <= '\u3191')
      return "OTHER_SYMBOL";
    else  if ('\u3192' <= shar && shar <= '\u3195')
      return "OTHER_NUMBER";
    else  if ('\u3196' <= shar && shar <= '\u319F')
      return "OTHER_SYMBOL";
    else  if ('\u31A0' <= shar && shar <= '\u31FF')
      return "UNASSIGNED";
    else  if ('\u3200' <= shar && shar <= '\u321C')
      return "OTHER_SYMBOL";
    else  if ('\u321D' <= shar && shar <= '\u321F')
      return "UNASSIGNED";
    else  if ('\u3220' <= shar && shar <= '\u3229')
      return "OTHER_NUMBER";
    else  if ('\u322A' <= shar && shar <= '\u3243')
      return "OTHER_SYMBOL";
    else  if ('\u3244' <= shar && shar <= '\u325F')
      return "UNASSIGNED";
    else  if ('\u3260' <= shar && shar <= '\u327B')
      return "OTHER_SYMBOL";
    else  if ('\u327C' <= shar && shar <= '\u327E')
      return "UNASSIGNED";
    else  if (shar == '\u327F')
      return "OTHER_SYMBOL";
    else  if ('\u3280' <= shar && shar <= '\u3289')
      return "OTHER_NUMBER";
    else  if ('\u328A' <= shar && shar <= '\u32B0')
      return "OTHER_SYMBOL";
    else  if ('\u32B1' <= shar && shar <= '\u32BF')
      return "UNASSIGNED";
    else  if ('\u32C0' <= shar && shar <= '\u32CB')
      return "OTHER_SYMBOL";
    else  if ('\u32CC' <= shar && shar <= '\u32CF')
      return "UNASSIGNED";
    else  if ('\u32D0' <= shar && shar <= '\u32FE')
      return "OTHER_SYMBOL";
    else  if (shar == '\u32FF')
      return "UNASSIGNED";
    else  if ('\u3300' <= shar && shar <= '\u3376')
      return "OTHER_SYMBOL";
    else  if ('\u3377' <= shar && shar <= '\u337A')
      return "UNASSIGNED";
    else  if ('\u337B' <= shar && shar <= '\u33DD')
      return "OTHER_SYMBOL";
    else  if ('\u33DE' <= shar && shar <= '\u33DF')
      return "UNASSIGNED";
    else  if ('\u33E0' <= shar && shar <= '\u33FE')
      return "OTHER_SYMBOL";
    else  if ('\u33FF' <= shar && shar <= '\u4DFF')
      return "UNASSIGNED";
    else  if ('\u4E00' <= shar && shar <= '\u9FA5')
      return "OTHER_LETTER";
    else  if ('\u9FA6' <= shar && shar <= '\uABFF')
      return "UNASSIGNED";
    else  if ('\uAC00' <= shar && shar <= '\uD7A3')
      return "OTHER_LETTER";
    else  if ('\uD7A4' <= shar && shar <= '\uD7FF')
      return "UNASSIGNED";
    else  if ('\uD800' <= shar && shar <= '\uDFFF')
      return "SURROGATE";
    else  if ('\uE000' <= shar && shar <= '\uF8FF')
      return "PRIVATE_USE";
    else  if ('\uF900' <= shar && shar <= '\uFA2D')
      return "OTHER_LETTER";
    else  if ('\uFA2E' <= shar && shar <= '\uFAFF')
      return "UNASSIGNED";
    else  if ('\uFB00' <= shar && shar <= '\uFB06')
      return "LOWERCASE_LETTER";
    else  if ('\uFB07' <= shar && shar <= '\uFB12')
      return "UNASSIGNED";
    else  if ('\uFB13' <= shar && shar <= '\uFB17')
      return "LOWERCASE_LETTER";
    else  if ('\uFB18' <= shar && shar <= '\uFB1D')
      return "UNASSIGNED";
    else  if (shar == '\uFB1E')
      return "NON_SPACING_MARK";
    else  if ('\uFB1F' <= shar && shar <= '\uFB28')
      return "OTHER_LETTER";
    else  if (shar == '\uFB29')
      return "MATH_SYMBOL";
    else  if ('\uFB2A' <= shar && shar <= '\uFB36')
      return "OTHER_LETTER";
    else  if (shar == '\uFB37')
      return "UNASSIGNED";
    else  if ('\uFB38' <= shar && shar <= '\uFB3C')
      return "OTHER_LETTER";
    else  if (shar == '\uFB3D')
      return "UNASSIGNED";
    else  if (shar == '\uFB3E')
      return "OTHER_LETTER";
    else  if (shar == '\uFB3F')
      return "UNASSIGNED";
    else  if ('\uFB40' <= shar && shar <= '\uFB41')
      return "OTHER_LETTER";
    else  if (shar == '\uFB42')
      return "UNASSIGNED";
    else  if ('\uFB43' <= shar && shar <= '\uFB44')
      return "OTHER_LETTER";
    else  if (shar == '\uFB45')
      return "UNASSIGNED";
    else  if ('\uFB46' <= shar && shar <= '\uFBB1')
      return "OTHER_LETTER";
    else  if ('\uFBB2' <= shar && shar <= '\uFBD2')
      return "UNASSIGNED";
    else  if ('\uFBD3' <= shar && shar <= '\uFD3D')
      return "OTHER_LETTER";
    else  if (shar == '\uFD3E')
      return "START_PUNCTUATION";
    else  if (shar == '\uFD3F')
      return "END_PUNCTUATION";
    else  if ('\uFD40' <= shar && shar <= '\uFD4F')
      return "UNASSIGNED";
    else  if ('\uFD50' <= shar && shar <= '\uFD8F')
      return "OTHER_LETTER";
    else  if ('\uFD90' <= shar && shar <= '\uFD91')
      return "UNASSIGNED";
    else  if ('\uFD92' <= shar && shar <= '\uFDC7')
      return "OTHER_LETTER";
    else  if ('\uFDC8' <= shar && shar <= '\uFDEF')
      return "UNASSIGNED";
    else  if ('\uFDF0' <= shar && shar <= '\uFDFB')
      return "OTHER_LETTER";
    else  if ('\uFDFC' <= shar && shar <= '\uFE1F')
      return "UNASSIGNED";
    else  if ('\uFE20' <= shar && shar <= '\uFE23')
      return "NON_SPACING_MARK";
    else  if ('\uFE24' <= shar && shar <= '\uFE2F')
      return "UNASSIGNED";
    else  if (shar == '\uFE30')
      return "OTHER_PUNCTUATION";
    else  if ('\uFE31' <= shar && shar <= '\uFE32')
      return "DASH_PUNCTUATION";
    else  if ('\uFE33' <= shar && shar <= '\uFE34')
      return "CONNECTOR_PUNCTUATION";
    else  if (shar == '\uFE35')
      return "START_PUNCTUATION";
    else  if (shar == '\uFE36')
      return "END_PUNCTUATION";
    else  if (shar == '\uFE37')
      return "START_PUNCTUATION";
    else  if (shar == '\uFE38')
      return "END_PUNCTUATION";
    else  if (shar == '\uFE39')
      return "START_PUNCTUATION";
    else  if (shar == '\uFE3A')
      return "END_PUNCTUATION";
    else  if (shar == '\uFE3B')
      return "START_PUNCTUATION";
    else  if (shar == '\uFE3C')
      return "END_PUNCTUATION";
    else  if (shar == '\uFE3D')
      return "START_PUNCTUATION";
    else  if (shar == '\uFE3E')
      return "END_PUNCTUATION";
    else  if (shar == '\uFE3F')
      return "START_PUNCTUATION";
    else  if (shar == '\uFE40')
      return "END_PUNCTUATION";
    else  if (shar == '\uFE41')
      return "START_PUNCTUATION";
    else  if (shar == '\uFE42')
      return "END_PUNCTUATION";
    else  if (shar == '\uFE43')
      return "START_PUNCTUATION";
    else  if (shar == '\uFE44')
      return "END_PUNCTUATION";
    else  if ('\uFE45' <= shar && shar <= '\uFE48')
      return "UNASSIGNED";
    else  if ('\uFE49' <= shar && shar <= '\uFE4C')
      return "OTHER_PUNCTUATION";
    else  if ('\uFE4D' <= shar && shar <= '\uFE4F')
      return "CONNECTOR_PUNCTUATION";
    else  if ('\uFE50' <= shar && shar <= '\uFE52')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\uFE53')
      return "UNASSIGNED";
    else  if ('\uFE54' <= shar && shar <= '\uFE57')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\uFE58')
      return "DASH_PUNCTUATION";
    else  if (shar == '\uFE59')
      return "START_PUNCTUATION";
    else  if (shar == '\uFE5A')
      return "END_PUNCTUATION";
    else  if (shar == '\uFE5B')
      return "START_PUNCTUATION";
    else  if (shar == '\uFE5C')
      return "END_PUNCTUATION";
    else  if (shar == '\uFE5D')
      return "START_PUNCTUATION";
    else  if (shar == '\uFE5E')
      return "END_PUNCTUATION";
    else  if ('\uFE5F' <= shar && shar <= '\uFE61')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\uFE62')
      return "MATH_SYMBOL";
    else  if (shar == '\uFE63')
      return "DASH_PUNCTUATION";
    else  if ('\uFE64' <= shar && shar <= '\uFE66')
      return "MATH_SYMBOL";
    else  if (shar == '\uFE67')
      return "UNASSIGNED";
    else  if (shar == '\uFE68')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\uFE69')
      return "CURRENCY_SYMBOL";
    else  if ('\uFE6A' <= shar && shar <= '\uFE6B')
      return "OTHER_PUNCTUATION";
    else  if ('\uFE6C' <= shar && shar <= '\uFE6F')
      return "UNASSIGNED";
    else  if ('\uFE70' <= shar && shar <= '\uFE72')
      return "OTHER_LETTER";
    else  if (shar == '\uFE73')
      return "UNASSIGNED";
    else  if (shar == '\uFE74')
      return "OTHER_LETTER";
    else  if (shar == '\uFE75')
      return "UNASSIGNED";
    else  if ('\uFE76' <= shar && shar <= '\uFEFC')
      return "OTHER_LETTER";
    else  if ('\uFEFD' <= shar && shar <= '\uFEFE')
      return "UNASSIGNED";
    else  if (shar == '\uFEFF')
      return "FORMAT";
    else  if (shar == '\uFF00')
      return "UNASSIGNED";
    else  if ('\uFF01' <= shar && shar <= '\uFF03')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\uFF04')
      return "CURRENCY_SYMBOL";
    else  if ('\uFF05' <= shar && shar <= '\uFF07')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\uFF08')
      return "START_PUNCTUATION";
    else  if (shar == '\uFF09')
      return "END_PUNCTUATION";
    else  if (shar == '\uFF0A')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\uFF0B')
      return "MATH_SYMBOL";
    else  if (shar == '\uFF0C')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\uFF0D')
      return "DASH_PUNCTUATION";
    else  if ('\uFF0E' <= shar && shar <= '\uFF0F')
      return "OTHER_PUNCTUATION";
    else  if ('\uFF10' <= shar && shar <= '\uFF19')
      return "DECIMAL_DIGIT_NUMBER";
    else  if ('\uFF1A' <= shar && shar <= '\uFF1B')
      return "OTHER_PUNCTUATION";
    else  if ('\uFF1C' <= shar && shar <= '\uFF1E')
      return "MATH_SYMBOL";
    else  if ('\uFF1F' <= shar && shar <= '\uFF20')
      return "OTHER_PUNCTUATION";
    else  if ('\uFF21' <= shar && shar <= '\uFF3A')
      return "UPPERCASE_LETTER";
    else  if (shar == '\uFF3B')
      return "START_PUNCTUATION";
    else  if (shar == '\uFF3C')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\uFF3D')
      return "END_PUNCTUATION";
    else  if (shar == '\uFF3E')
      return "MODIFIER_SYMBOL";
    else  if (shar == '\uFF3F')
      return "CONNECTOR_PUNCTUATION";
    else  if (shar == '\uFF40')
      return "MODIFIER_SYMBOL";
    else  if ('\uFF41' <= shar && shar <= '\uFF5A')
      return "LOWERCASE_LETTER";
    else  if (shar == '\uFF5B')
      return "START_PUNCTUATION";
    else  if (shar == '\uFF5C')
      return "MATH_SYMBOL";
    else  if (shar == '\uFF5D')
      return "END_PUNCTUATION";
    else  if (shar == '\uFF5E')
      return "MATH_SYMBOL";
    else  if ('\uFF5F' <= shar && shar <= '\uFF60')
      return "UNASSIGNED";
    else  if (shar == '\uFF61')
      return "OTHER_PUNCTUATION";
    else  if (shar == '\uFF62')
      return "START_PUNCTUATION";
    else  if (shar == '\uFF63')
      return "END_PUNCTUATION";
    else  if ('\uFF64' <= shar && shar <= '\uFF65')
      return "OTHER_PUNCTUATION";
    else  if ('\uFF66' <= shar && shar <= '\uFF6F')
      return "OTHER_LETTER";
    else  if (shar == '\uFF70')
      return "MODIFIER_LETTER";
    else  if ('\uFF71' <= shar && shar <= '\uFF9D')
      return "OTHER_LETTER";
    else  if ('\uFF9E' <= shar && shar <= '\uFF9F')
      return "MODIFIER_LETTER";
    else  if ('\uFFA0' <= shar && shar <= '\uFFBE')
      return "OTHER_LETTER";
    else  if ('\uFFBF' <= shar && shar <= '\uFFC1')
      return "UNASSIGNED";
    else  if ('\uFFC2' <= shar && shar <= '\uFFC7')
      return "OTHER_LETTER";
    else  if ('\uFFC8' <= shar && shar <= '\uFFC9')
      return "UNASSIGNED";
    else  if ('\uFFCA' <= shar && shar <= '\uFFCF')
      return "OTHER_LETTER";
    else  if ('\uFFD0' <= shar && shar <= '\uFFD1')
      return "UNASSIGNED";
    else  if ('\uFFD2' <= shar && shar <= '\uFFD7')
      return "OTHER_LETTER";
    else  if ('\uFFD8' <= shar && shar <= '\uFFD9')
      return "UNASSIGNED";
    else  if ('\uFFDA' <= shar && shar <= '\uFFDC')
      return "OTHER_LETTER";
    else  if ('\uFFDD' <= shar && shar <= '\uFFDF')
      return "UNASSIGNED";
    else  if ('\uFFE0' <= shar && shar <= '\uFFE1')
      return "CURRENCY_SYMBOL";
    else  if (shar == '\uFFE2')
      return "MATH_SYMBOL";
    else  if (shar == '\uFFE3')
      return "MODIFIER_SYMBOL";
    else  if (shar == '\uFFE4')
      return "OTHER_SYMBOL";
    else  if ('\uFFE5' <= shar && shar <= '\uFFE6')
      return "CURRENCY_SYMBOL";
    else  if (shar == '\uFFE7')
      return "UNASSIGNED";
    else  if ('\uFFE8' <= shar && shar <= '\uFFEC')
      return "MATH_SYMBOL";
    else  if ('\uFFED' <= shar && shar <= '\uFFEE')
      return "OTHER_SYMBOL";
    else  if ('\uFFEF' <= shar && shar <= '\uFFFB')
      return "UNASSIGNED";
    else
      return "OTHER_SYMBOL";
  }
}