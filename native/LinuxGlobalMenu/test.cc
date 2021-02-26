//
// Created by parallels on 8/1/18.
//

#include "DbusMenuWrapper.h"

#include <gtk/gtk.h>
#include <gdk/gdkx.h>

static void _onDestroyWindow(void) {
    gtk_main_quit();
    return;
}

static void _testHandler(int uid, int evtype) {
    int n = 0;
}

int main (int argv, char ** argc) {
    gtk_init(&argv, &argc);

    GtkWidget * window = gtk_window_new(GTK_WINDOW_TOPLEVEL);
    g_signal_connect(G_OBJECT(window), "destroy", G_CALLBACK(_onDestroyWindow), NULL);

    gtk_widget_show(window);

    // create register menu object for window
    long wndxid = GDK_WINDOW_XID (gtk_widget_get_window (window));
    WndInfo* wi = registerWindow(wndxid, &_testHandler);

    // populate menu
    DbusmenuMenuitem* root1 = addRootMenu(wi, 1, "root1");

    FILE *f;
    char buffer[1024*1024];
    gsize length;

    DbusmenuMenuitem* item1 = addMenuItem(root1, 2, "item1", ITEM_SIMPLE, 0);
//    setItemIcon(item1, buffer, length);
    DbusmenuMenuitem* sub1 = addMenuItem(item1, 3, "sub1", ITEM_SUBMENU, 0);
//    setItemIcon(sub1, buffer, length);

    // run main loop
    gtk_main();

    return 0;
}
