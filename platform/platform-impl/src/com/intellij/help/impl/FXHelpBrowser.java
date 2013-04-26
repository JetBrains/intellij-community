/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
package com.intellij.help.impl;

import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.help.*;
import javax.help.search.*;
import java.awt.Dialog;
import java.awt.BorderLayout;
import javax.swing.*;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Locale;

*/
/**
 * @author Denis.Fokin
 *//*


public class FXHelpBrowser  implements SearchListener {

  private static final Logger LOG = Logger.getInstance("#com.intellij.help.impl.FXHelpBrowser");

  private TreeView<TOCItemWrapper> tocTreeView = null;
  private ListView<SearchItemWrapper> searchResultsList = null;
  private WebView webView = null;

  final VBox bodyVBox = new VBox(5);
  final HBox contentHBox = new HBox(5);
  final HelpSet helpSet;
  final JHelp jHelp;
  final JDialog helpDialog;
  final JFXPanel fxPanel;
  WebEngine webEngine;

  public FXHelpBrowser(HelpSet helpSet) {
    this.helpSet = helpSet;
    jHelp = new JHelp(helpSet);
    helpDialog = new JDialog(null, jHelp.getModel().getDocumentTitle(), Dialog.ModalityType.MODELESS);
    fxPanel = new JFXPanel();
  }

  private boolean isHelpBrowserInitialized = false;

  private void initHelpBrowser () {
    if (isHelpBrowserInitialized) return ;
    Platform.runLater(new Runnable() {
      @Override
      public void run() {
        tocTreeView = new TreeView<TOCItemWrapper>();
        searchResultsList = new ListView<SearchItemWrapper>();
        webView = new WebView();
        isHelpBrowserInitialized = true;
        webEngine = webView.getEngine();
      }
    });
  }

  void showDocumentation(@Nullable String id) {

    initHelpBrowser();
    loadPageInWebView(id);

    URL url = null;

    try {
      url = new URL(helpSet.getHelpSetURL(), "HelpTOCij.xml");
    }
    catch (IOException e) {
      LOG.error(e);
    }

    IdeaHelpTOCParser tocParser = new IdeaHelpTOCParser();
    final TreeItem<TOCItemWrapper> rootNode = tocParser.parse(url);

    Platform.runLater(new Runnable() {
      public void run() {
        initHelpDialog(rootNode);
        packHelpDialogAndShowOnEDT();
        tocTreeView.requestFocus();
      }
    });
  }

  private boolean isHelpDialogInitiated = false;

  private void initHelpDialog(TreeItem<TOCItemWrapper> rootNode) {
    if (isHelpDialogInitiated) return;

    Group root = new Group();
    Scene scene = new Scene(root, Color.ANTIQUEWHITE);

    fxPanel.setScene(scene);

    tocTreeView.setShowRoot(true);
    tocTreeView.setRoot(rootNode);
    rootNode.setExpanded(true);

    tocTreeView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    tocTreeView.setShowRoot(false);
    tocTreeView.setEditable(false);
    tocTreeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    tocTreeView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener() {
      @Override
      public void changed(ObservableValue observable, Object oldValue,
                          Object newValue) {
        final TreeItem<TOCItemWrapper> item = tocTreeView.getSelectionModel().getSelectedItem();
        if (item != null) {
          webEngine.load(item.getValue().get().getURL().toString());
        }
      }
    });

    searchResultsList.getSelectionModel().selectedItemProperty().addListener(searchResultsListener);

    contentHBox.getChildren().setAll(tocTreeView, webView);

    HBox.setHgrow(tocTreeView, Priority.ALWAYS);

    SearchField searchField = new SearchField();

    bodyVBox.getChildren().addAll(searchField, contentHBox);

    searchField.myField.setOnAction(new EventHandler<ActionEvent>() {
      @Override
      public void handle(ActionEvent event) {
        SearchView searchView = (SearchView)helpSet.getNavigatorView("Search");
        MergingSearchEngine search = new MergingSearchEngine(searchView);

        try {

          SearchQuery searchquery = search.createQuery();
          searchquery.addSearchListener(FXHelpBrowser.this);


          if (searchquery.isActive()) {
            searchquery.stop();
          }
          searchquery.start("button", Locale.getDefault());
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });

    Tab tab1 = new Tab();
    tab1.setText("Search");

    root.getChildren().add(bodyVBox);


    isHelpDialogInitiated = true;
  }

  private void packHelpDialogAndShowOnEDT() {
    LaterInvocator.invokeLater(new Runnable() {
      @Override
      public void run() {
*/
/*      helpDialog.invalidate();
        helpDialog.validate();
        helpDialog.repaint();*//*


        helpDialog.getContentPane().add(fxPanel, BorderLayout.CENTER);
        helpDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        helpDialog.setSize(800, 600);
        helpDialog.setVisible(true);
      }
    });
  }

  private void loadPageInWebView(@Nullable final String id) {

    jHelp.setCurrentID(id);
    final URL currentURL = jHelp.getModel().getCurrentURL();

    Platform.runLater(new Runnable() {
      @Override
      public void run() {
        webEngine.load(currentURL.toString());

        webEngine.documentProperty().addListener(new ChangeListener<Document>() {
          @Override
          public void changed(ObservableValue<? extends Document> value, Document document, Document document2) {
            //highlightText("");
          }
        });
      }
    });
  }

  private void highlightText(String text) {
    webView.getEngine().executeScript("");
  }



  private ChangeListener searchResultsListener = new ChangeListener() {
    @Override
    public void changed(ObservableValue observable, Object oldValue,
                        Object newValue) {
      SearchItemWrapper wrapper = searchResultsList.getSelectionModel().getSelectedItem();

      if (wrapper != null) {
        final SearchItem item = wrapper.get();

        Platform.runLater(new Runnable() {
          @Override
          public void run() {
            try {
              webView.getEngine().load(new URL(item.getBase(), item.getFilename()).toString());

            }
            catch (MalformedURLException e) {
              LOG.error(e);
            }
          }
        }
        );
      } else {
        Platform.runLater(new Runnable() {
          @Override
          public void run() {
            webView.getEngine().load("about:blank");
          }
        }
        );

      }
    }
  };

  @Override
  public void itemsFound(SearchEvent e) {
    final Enumeration enumeration = e.getSearchItems();

    Platform.runLater(new Runnable() {
      public void run () {
        searchResultsList.getItems().clear();

        while (enumeration.hasMoreElements()) {
          SearchItem item = (SearchItem)enumeration.nextElement();
          searchResultsList.getItems().add(new SearchItemWrapper(item));
        }

        searchResultsList.getSelectionModel().selectFirst();

        contentHBox.getChildren().clear();
        contentHBox.getChildren().addAll(searchResultsList, webView);

        SearchItem item = searchResultsList.getSelectionModel().getSelectedItem().get();
        try {
          webView.getEngine().load(new URL(item.getBase(), item.getFilename()).toString());
        }
        catch (MalformedURLException e) {
          LOG.error(e);
        }

        searchResultsList.requestFocus();
      }});

  }

  @Override
  public void searchStarted(SearchEvent e) {
    LOG.debug("Search started");
  }

  @Override
  public void searchFinished(SearchEvent e) {
    LOG.debug("Search finished");
  }

  private static class IdeaHelpTOCParser {
    SAXParser parser;
    TreeItem<TOCItemWrapper> root = new TreeItem<TOCItemWrapper>();

    DefaultHandler handler = new DefaultHandler() {
      @Override
      public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        super.startElement(uri, localName, qName, attributes);

      }
    };

    private IdeaHelpTOCParser() {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      try {
        parser = factory.newSAXParser();
      }
      catch (ParserConfigurationException e) {
        LOG.error(e);
      }
      catch (SAXException e) {
        LOG.error(e);
      }
    }

    private TreeItem<TOCItemWrapper> parse(URL url) {
      try {
        parser.parse(url.toURI().toString(), handler);
      }
      catch (SAXException e) {
        LOG.error(e);
      }
      catch (IOException e) {
        LOG.error(e);
      }
      catch (URISyntaxException e) {
        LOG.error(e);
      }
      return root;
    }
  }

  private static class SearchField extends Region {

    private TextField myField;

    public SearchField() {
      setMinHeight(24);
      setPrefSize(200, 24);
      myField = new TextField();
      myField.setPromptText("Search");
      setMaxSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
      getChildren().add(myField);
    }
  }

  private static class SearchItemWrapper {
    final SearchItem item;

    SearchItemWrapper(SearchItem item) {
      this.item = item;
    }

    SearchItem get() {
      return item;
    }

    @Override
    public String toString() {
      return item.getTitle();
    }
  }

  private static class TOCItemWrapper {

    final private TOCItem item;

    TOCItemWrapper(TOCItem item) {
      this.item = item;
    }

    TOCItem get() {
      return item;
    }

    @Override
    public String toString() {
      return item.getName();
    }
  }
}
*/
