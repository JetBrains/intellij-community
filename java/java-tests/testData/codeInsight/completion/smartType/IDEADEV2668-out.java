class BigDecimal{}

enum Treasures {
    DIAMOND(new BigDecimal())<caret>;

    Treasures(BigDecimal value) {
        this.value = value;
    }

    private BigDecimal value;
}