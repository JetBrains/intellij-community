// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.JsonReaderEx;
import org.jetbrains.io.JsonUtil;

import javax.swing.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author Alexander Lobas
 */
public final class PluginPriceService {
  private static final Logger LOG = Logger.getInstance(PluginPriceService.class);

  private static final DecimalFormat FORMAT = new DecimalFormat("###.#");

  private static final Map<String, Object> myPriceTable = new HashMap<>();
  private static boolean myPrepared;
  private static boolean myPreparing;

  public static void getPrice(@NotNull IdeaPluginDescriptor descriptor,
                              @NotNull Consumer<? super String> callback,
                              @NotNull Consumer<? super String> asyncCallback) {
    checkAccess();

    String code = descriptor.getProductCode();

    if (myPrepared) {
      Object value = myPriceTable.get(code);
      if (value instanceof String) {
        callback.consume((String)value);
      }
    }
    else {
      myPriceTable.put(code, asyncCallback);

      if (!myPreparing) {
        myPreparing = true;
        loadPrice();
      }
    }
  }

  public static void cancel() {
    checkAccess();
    clear();
  }

  private static void clear() {
    for (Iterator<Entry<String, Object>> I = myPriceTable.entrySet().iterator(); I.hasNext(); ) {
      Entry<String, Object> entry = I.next();
      if (entry.getValue() instanceof Consumer) {
        I.remove();
      }
    }
  }

  private static void loadPrice() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        Object priceJson = getPluginPricesJsonObject();

        if (priceJson instanceof Map) {
          Map<String, String> result = parsePrices((Map)priceJson);

          ApplicationManager.getApplication().invokeLater(() -> {
            checkAccess();

            for (Entry<String, String> entry : result.entrySet()) {
              Object callback = myPriceTable.put(entry.getKey(), entry.getValue());
              if (callback instanceof Consumer) {
                ((Consumer<String>)callback).consume(entry.getValue());
              }
            }

            clear();
            myPrepared = true;
            myPreparing = false;
          }, ModalityState.any());
        }
      }
      catch (IOException e) {
        e.printStackTrace();
        LOG.debug(e);
      }
    });
  }

  @Nullable
  private static Object getPluginPricesJsonObject() throws IOException {
    ApplicationInfoEx instance = ApplicationInfoImpl.getShadowInstance();
    Url url = Urls.newFromEncoded(instance.getPluginManagerUrl() + "/geo/files/prices");
    return HttpRequests.request(url).throwStatusCodeException(false).productNameAsUserAgent().connect(request -> {
      URLConnection connection = request.getConnection();

      if (connection instanceof HttpURLConnection && ((HttpURLConnection)connection).getResponseCode() != HttpURLConnection.HTTP_OK) {
        return null;
      }

      try (JsonReaderEx json = new JsonReaderEx(FileUtil.loadTextAndClose(request.getReader()))) {
        return JsonUtil.nextAny(json);
      }
    });
  }

  @NotNull
  private static Map<String, String> parsePrices(@NotNull Map<String, Object> jsonObject) {
    Map<String, String> result = new HashMap<>();
    Object plugins = jsonObject.get("plugins");

    if (plugins instanceof List) {
      String currency = parseCurrency(jsonObject);

      for (Map<String, Object> plugin : (List<Map<String, Object>>)plugins) {
        Object code = plugin.get("code");
        if (!(code instanceof String)) {
          continue;
        }

        Double price = parsePrice(plugin);
        if (price != null) {
          result.put((String)code, currency + FORMAT.format(price));
        }
      }
    }

    return result;
  }

  @NotNull
  private static String parseCurrency(@NotNull Map<String, Object> jsonObject) {
    Object iso = jsonObject.get("iso");
    if (iso instanceof String) {
      Currency currency = Currency.getInstance((String)iso);
      if (currency != null) {
        return currency.getSymbol(Locale.ENGLISH);
      }
    }
    return "";
  }

  @Nullable
  private static Double parsePrice(@NotNull Map<String, Object> plugin) {
    double[] personal = parsePrice(plugin, "personal");
    double[] commercial = parsePrice(plugin, "commercial");

    if (personal == null && commercial == null) {
      return null;
    }
    if (personal == null || commercial == null) {
      for (double value : personal == null ? commercial : personal) {
        if (value > 0) {
          return value;
        }
      }
    }
    else {
      for (int i = 0; i < 2; i++) {
        if (personal[i] > 0 && commercial[i] > 0) {
          return Math.min(personal[i], commercial[i]);
        }
      }
    }

    return null;
  }

  @Nullable
  private static double[] parsePrice(@NotNull Map<String, Object> jsonObject, @NotNull String key) {
    Object value = jsonObject.get(key);

    if (value instanceof Map) {
      Object subscription = ((Map)value).get("subscription");
      if (subscription instanceof Map) {
        return new double[]{parsePriceValue((Map)subscription, "monthly"), parsePriceValue((Map)subscription, "annual")};
      }
    }

    return null;
  }

  private static double parsePriceValue(@NotNull Map<String, Object> jsonObject, @NotNull String key) {
    Object value = jsonObject.get(key);
    if (value instanceof Double) {
      return (double)value;
    }
    if (value instanceof String) {
      try {
        return Double.parseDouble((String)value);
      }
      catch (NumberFormatException ignore) {
      }
    }
    return 0;
  }

  private static void checkAccess() {
    assert SwingUtilities.isEventDispatchThread();
  }
}