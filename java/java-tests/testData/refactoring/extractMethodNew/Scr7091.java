public class IdeaTestBug {
    public static final int MAP_HEIGHT = 1000;

    Object[][] _map;

    public int checkAndRemoveConditions() {
        int conditionsFound = 0;
        for (int y = 0; y < MAP_HEIGHT; y++) {
            while (isCheckCondition(y)) {
                ++conditionsFound;

                <selection>final Object[] temp = _map[y];
                for (int x = 0; x < temp.length; x++) {
                    temp[x] = null;
                }
                for (int yy = y + 1; yy < MAP_HEIGHT; ++yy) {
                    _map[yy - 1] = _map[yy];
                }
                _map[MAP_HEIGHT - 1] = temp;</selection>
            }
        }
        return conditionsFound;
    }

    private boolean isCheckCondition(int y) {
        return false;
    }

}