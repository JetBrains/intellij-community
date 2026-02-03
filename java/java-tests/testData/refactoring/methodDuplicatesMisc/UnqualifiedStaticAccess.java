public class aa {
    public static boolean isVisualUpdate(int update_type) {
        return update_type <= Iaaa.FULL_QUOTE_VISUAL;
    }
}

interface Iaaa {
    int FULL_QUOTE_VISUAL = 0;
}

class a implements Iaaa {
    int update_type = 8;

    public static boolean blab<caret>la(int update_type) {
        return update_type <= FULL_QUOTE_VISUAL;
    }
}