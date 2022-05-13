enum WarningAndError {
    A;

    public boolean isFirstChild() {
        return false;
    }

    enum Colors {
        SPADES,
        DIAMOND,
        HEARTS,
        CLUBS;
        <caret>private BigDecimal value;
        public enum Direction {
            UP, DOWN;
        }

        Colors() {
        }
    }
}