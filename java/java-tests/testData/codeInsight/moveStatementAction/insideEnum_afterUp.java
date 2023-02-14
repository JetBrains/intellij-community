enum WarningAndError {
    A;

    public boolean isFirstChild() {
        return false;
    }

    <caret>private BigDecimal value;
    enum Colors {
        SPADES,
        DIAMOND,
        HEARTS,
        CLUBS;
        public enum Direction {
            UP, DOWN;
        }

        Colors() {
        }
    }
}