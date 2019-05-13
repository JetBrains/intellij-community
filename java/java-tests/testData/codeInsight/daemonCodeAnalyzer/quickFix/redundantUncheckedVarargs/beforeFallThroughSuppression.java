// "Remove 'fallthrough' suppression" "false"
class MyTest {

    private static void unescapeStringCharacters(int length, @NotNull String s, @NotNull StringBuilder buffer) {
        boolean escaped = false;
        for (int idx = 0; idx < length; idx++) {
            char ch = s.charAt(idx);
            if (!escaped) {
                if (ch == '\\') {
                    escaped = true;
                } else {
                    buffer.append(ch);
                }
            } else {
                int octalEscapeMaxLength = 2;
                switch (ch) {
                    case 'n':
                        buffer.append('\n');
                        break;

                    case 'r':
                        buffer.append('\r');
                        break;

                    case 'b':
                        buffer.append('\b');
                        break;

                    case 't':
                        buffer.append('\t');
                        break;

                    case 'f':
                        buffer.append('\f');
                        break;

                    case '\'':
                        buffer.append('\'');
                        break;

                    case '\"':
                        buffer.append('\"');
                        break;

                    case '\\':
                        buffer.append('\\');
                        break;

                    case 'u':
                        if (idx + 4 < length) {
                            try {
                                int code = Integer.parseInt(s.substring(idx + 1, idx + 5), 16);
                                //noinspection AssignmentToForLoopParameter
                                idx += 4;
                                buffer.append((char) code);
                            } catch (NumberFormatException e) {
                                buffer.append("\\u");
                            }
                        } else {
                            buffer.append("\\u");
                        }
                        break;

                    case '0':
                    case '1':
                    case '2':
                    case '3':
                        octalEscapeMaxLength = 3;
                        //noinspection fall<caret>through
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                        int escapeEnd = idx + 1;
                        while (escapeEnd < length && escapeEnd < idx + octalEscapeMaxLength)
                            escapeEnd++;
                        try {
                            buffer.append((char) Integer.parseInt(s.substring(idx, escapeEnd), 8));
                        } catch (NumberFormatException e) {
                            throw new RuntimeException("Couldn't parse " + s.substring(idx, escapeEnd), e); // shouldn't happen
                        }
                        //noinspection AssignmentToForLoopParameter
                        idx = escapeEnd - 1;
                        break;

                    default:
                        buffer.append(ch);
                        break;
                }
                escaped = false;
            }
        }

        if (escaped) buffer.append('\\');
    }
}