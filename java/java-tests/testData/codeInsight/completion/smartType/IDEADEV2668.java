class BigDecimal{}

enum Treasures {
    DIAMOND(new <caret>);

    Treasures(BigDecimal value) {
        this.value = value;
    }

    private BigDecimal value;
}