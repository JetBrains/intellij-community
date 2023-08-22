import java.util.List;

public class WidenMismatch {
  void listSize(WidenMismatch m) {
    int N = 200;
    while (m.makeList().size() == N + 1 + N) {
      System.gc();
    }
  }
  
  native List<String> makeList();
  
  int test(int value) {
    if (value < 100) {
      return value;
    }
    else if (value % 10 < 5) {
      return value - value % 10;
    }
    else {
      return value + 10 - value % 10;
    }
  }

  void test2(int n) {
    int members = 5 * n;
    int y = 100 + members * 40 * 3;
  }

  int myRelativeLevel;

  private String getDots() {
    String dots = "";
    for (int i = 0; i < myRelativeLevel; i += 1) {
      dots += ".";
    }
    return dots;
  }

  long longDecode(String s) {
    if (s == null || s.length() == 0) {
      return 0;
    }
    int multiplier;
    switch (s.charAt(s.length() - 1)) {
      case 'K':
        multiplier = 1000;
        break;
      case 'M':
        multiplier = 1000000;
        break;
      case 'G':
        multiplier = 1000000000;
        break;
      default:
        multiplier = 1;
    }
    try {
      if (multiplier == 1) {
        return Long.decode(s);
      }
      else {
        return Long.decode(s.substring(0, s.length() - 1)).longValue() * multiplier;
      }
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  void test(String testName) {
    int result = 0;
    int multiplier = 1;

    for(int i = testName.length() - 1; i >=0 ; --i) {
      final char ch = testName.charAt(i);
      if (Character.isDigit(ch)) {
        result += (ch - '0')* multiplier;
        multiplier *= 10;
      } else {
        break;
      }
    }
  }

  int parse(int len, int ret) {
    int k = ((ret >> 12) & 0xF) * 0x11000000;
    k |= ((ret >> 8) & 0xF) * 0x110000;
    return ret;
  }
}