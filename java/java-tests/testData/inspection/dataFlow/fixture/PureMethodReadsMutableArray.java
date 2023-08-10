// IDEA-286208
public class PureMethodReadsMutableArray {
  static char[] dic = new char[128], cnt = new char[128];
  public static void main(String[] args) {
    if (check()) {
      cnt[0]=1;
      if (check()) {}
    }
    if (check()) {
      cnt[args.length]=1;
      if (check()) {}
    }
    if (check()) {
      cnt[0]++;
      if (check()) {}
    }
  }

  public static boolean check() {
    for (int i = 0; i < 128; i++) {
      if (dic[i] > cnt[i]) {
        return false;
      }
    }
    return true;
  }
}