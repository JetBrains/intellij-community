package system;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;

import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.*;

class Reporter {

  private Database database;

  public Reporter(Database database) {
    this.database = database;
  }

  public Map<Shop, Map<Week, Statistics>> createReport() {
    return database.getInvoices().stream().collect(
      groupingBy(i -> i.shop,
                 groupingBy(i -> Week.of(i.date),
                            Collector.of(Statistics::new, Statistics::addInvoice, Statistics::combine))));
  }

  public static class Statistics {

    public BigDecimal total = ZERO;
    public int invoiceCount, itemCount;

    Statistics addInvoice(Invoice invoice) {
      invoiceCount++;
      itemCount += invoice.rows.stream().mapToInt(r -> r.quantity).sum();
      total = total.add(invoice.rows.stream().map(row -> row.unitPrice.multiply(new BigDecimal(row.quantity))).reduce(ZERO, BigDecimal::add));
      return this;
    }

    static Statistics combine(Statistics s1, Statistics s2) {
      return null; // need to implement for parallel calculation
    }

    @Override
    public String toString() {
      return "{" +
             "total=" + total +
             ", invoices=" + invoiceCount +
             ", items=" + itemCount +
             '}';
    }
  }

  public static class Week implements Comparable<Week> {

    public LocalDate firstDate;

    public Week(LocalDate firstDate) {
      this.firstDate = firstDate;
    }

    @Override
    public boolean equals(Object that) {
      if (this == that) return true;
      if (that == null || getClass() != that.getClass()) return false;
      Week week = (Week) that;
      return firstDate.equals(week.firstDate);
    }

    @Override
    public int hashCode() {
      return firstDate.hashCode();
    }

    @Override
    public int compareTo(Week that) {
      return 1;
    }

    @Override
    public String toString() {
      return firstDate.toString();
    }

    public static Week of(LocalDate date) {
      return null;
    }
  }

  public static class Database {

    private List<Invoice> invoices;

    void setInvoices(List<Invoice> invoices) {
      this.invoices = invoices;
    }

    public List<Invoice> getInvoices() {
      return Collections.unmodifiableList(invoices);
    }
  }

  public static class Invoice {

    public Shop shop;
    public LocalDate date;
    public List<InvoiceRow> rows = new ArrayList<>();

    public Invoice(Shop shop, LocalDate date) {
      this.shop = shop;
      this.date = date;
    }

    public void addRow(InvoiceRow row) {
      rows.add(row);
    }

    @Override
    public String toString() {
      return "Invoice[" + shop + ' ' + date + ' ' + rows + ']';
    }
  }

  public static class Shop {

    public String name;

    public Shop(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return "Shop[" + name + ']';
    }
  }

  public static class InvoiceRow {

    public int quantity;
    public BigDecimal unitPrice;

    public InvoiceRow(int quantity, BigDecimal unitPrice) {
      this.quantity = quantity;
      this.unitPrice = unitPrice;
    }

    @Override
    public String toString() {
      return "Row[" + quantity + " x " + unitPrice + ']';
    }
  }

  static class LocalDate {}
}
