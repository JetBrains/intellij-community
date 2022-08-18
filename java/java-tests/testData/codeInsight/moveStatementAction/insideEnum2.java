enum WarningAndError {
    A;

    public boolean isFirstChild() {
        return false;
    }


    enum Colors {
        SPADES,
        DIAMOND,
        HEARTS,
        CLUBS

        private BigDecimal value;<caret>
        public enum Direction {
            UP, DOWN
        }

        Colors() {
        }


    }


}