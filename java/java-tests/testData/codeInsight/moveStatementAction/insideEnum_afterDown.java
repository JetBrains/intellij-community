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
        public enum Direction {
            UP, DOWN;
            private BigDecimal value;
        }

        Colors() {
        }
    }
}