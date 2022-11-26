// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.inspectopedia.extractor.utils;

import com.intellij.lang.Language;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HtmlUtils {
    public static final Safelist SAFELIST = new Safelist();

    static {
        SAFELIST.addTags("a", "b", "code", "i", "li", "list", "p", "s", "u");
    }

    private static final List<Pair<String, String>> RENAME_MAP = List.of(
            Pair.create("ul", "list"),
            Pair.create("th", "td"),
            Pair.create("c", "code"),
            Pair.create("strong", "b"),
            Pair.create("small", "font"),
            Pair.create("span", "control"),
            Pair.create("blockquote", "tip"),
            Pair.create("em", "i")
    );

    private static final List<String> REMOVE_MAP = List.of(
            "hr",
            "br",
            "code:matches(^\\s*$)"
    );

    private static final List<String> UNWRAP_MAP = List.of(
            "tbody",
            "pre",
            "code[style=block] > *"
    );

    @NotNull
    public static String cleanupHtml(@NotNull String source, @Nullable String languageForCodeBlocks) {
        final Document document = Jsoup.parse(source);

        RENAME_MAP.forEach(map -> document.select(map.first).tagName(map.second));

        final Elements ol = document.select("ol");
        ol.tagName("list");
        ol.attr("style", "decimal");

        UNWRAP_MAP.forEach(map -> document.select(map).unwrap());

        final Elements codeBlock = document.select("pre > code");
        codeBlock.attr("style", "block");
        codeBlock.attr("lang", languageForCodeBlocks == null ? "Text" : languageForCodeBlocks);

        document.select("code > *").stream()
                .filter(element -> !element.tagName().equals("a"))
                .forEach(Node::unwrap);

        document.select("br").stream().map(Element::parent)
                .distinct()
                .forEach(parent -> {
                    final List<Pair<Boolean, List<Node>>> groups = new ArrayList<>();
                    final List<Node> inlineElements = new ArrayList<>();
                    final Iterator<Node> childNodes = parent.childNodes().iterator();
                    while (childNodes.hasNext()) {
                        final Node childNode = childNodes.next();

                        if (!isBlockElement(childNode)) {
                            inlineElements.add(childNode);
                        }

                        if (isBlockElement(childNode) || !childNodes.hasNext()) {
                            if (!inlineElements.isEmpty()) {
                                groups.add(Pair.create(true, List.copyOf(inlineElements)));
                                inlineElements.clear();
                            }
                        }

                        if (isBlockElement(childNode) && !isBr(childNode)) {
                            groups.add(Pair.create(false, List.of(childNode)));
                        }
                    }
                    for (Pair<Boolean, List<Node>> group : groups) {
                        final Boolean shouldWrap = group.getFirst();
                        final List<Node> nodes = group.getSecond();

                        final Element elementForNodes = shouldWrap ? document.createElement("p") : parent;

                        nodes.forEach(n -> {
                            n.remove();
                            elementForNodes.appendChild(n);
                        });

                        if (shouldWrap) {
                            parent.appendChild(elementForNodes);
                        }
                    }
                });

        REMOVE_MAP.forEach(map -> document.select(map).remove());

        Elements paragraphsWithParagraphs;
//What if there are hypothetically many nested P, and we're going to miss them with only one iteration?
        do {
            paragraphsWithParagraphs = document.select("p:has(p)");
            paragraphsWithParagraphs.unwrap();
        } while (!paragraphsWithParagraphs.isEmpty());
//And then there were multi nested paragraphs which deep down contained nothing but whitespace? Now they're ready for removal as well :)
        final Elements emptyParagraphs = document.select("p:matches(^\\s*$)");
        emptyParagraphs.remove();

        final Cleaner cleaner = new Cleaner(SAFELIST);
        cleaner.clean(document);

        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        document.outputSettings().prettyPrint(false);

        return document.body().html();
    }

    private static boolean isBlockElement(@NotNull Node node) {
        if (!(node instanceof Element))
            return false;

        final Element element = (Element) node;
        return element.tagName().equals("list") ||
                (element.tagName().equals("code") && element.attr("style").equals("block")) ||
                isBr(node);
    }

    private static boolean isBr(@NotNull Node node) {
        if (!(node instanceof Element))
            return false;

        return "br".equals(((Element) node).tagName());
    }
}
