class Scratch {
  public static void main(String[] args) {
    for (int i = 0; i < 52; i++) {
      System.out.println(getCardRank(i));
    }
  }
  public static String getCardRank(int card) {
    if (card < 0 || card > 51) {
      return "";
    }
    String result = "";
    int mod = (card % 13) + 1;
    if (mod == 1) {
      result += "A";
    } else if (mod == 11) {
      result += "J";
    } else if (mod == 12) {
      result += "Q";
    } else if (mod == 13) {
      result += "K";
    } else {
      result += "" + mod;
    }
    if (<warning descr="Condition 'mod == 0' is always 'false'">mod == 0</warning>) {}
    if (<warning descr="Condition 'mod == 14' is always 'false'">mod == 14</warning>) {}
    return result;
  }
}