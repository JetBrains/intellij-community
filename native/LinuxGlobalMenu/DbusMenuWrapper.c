#include <string.h>
#include <stdlib.h>
#include <stdarg.h>
#include <stdio.h>

#include <X11/Xlib.h>
#include <X11/keysym.h>

#include <gio/gio.h>
#include <libdbusmenu-glib/server.h>

#include "DbusMenuWrapper.h"

#define  DBUS_NAME   "com.canonical.AppMenu.Registrar"
#define  REG_IFACE  "com.canonical.AppMenu.Registrar"
#define  REG_OBJECT "/com/canonical/AppMenu/Registrar"

#define  MENUITEM_JHANDLER_PROPERTY "com.intellij.idea.globalmenu.jhandler"
#define  MENUITEM_UID_PROPERTY "com.intellij.idea.globalmenu.uid"

static jlogger _ourLogger = NULL;
static jrunnable _ourOnServiceAppearedCallback = NULL;
static jrunnable _ourOnServiceVanishedCallback = NULL;

static GMainContext * glib_main_context = NULL;

typedef struct _WndInfo {
  guint32 xid;
  char *menuPath;
  GDBusProxy *registrar;
  DbusmenuServer *server;
  DbusmenuMenuitem *menuroot;
  jeventcallback jhandler;
  jrunnable onReleaseCallback;
  GList* linkedXids;
} WndInfo;

static void _error(const char *msg) {
  if (_ourLogger != NULL)
    (*_ourLogger)(LOG_LEVEL_ERROR, msg);
}

static void _info(const char *msg) {
  if (_ourLogger != NULL)
    (*_ourLogger)(LOG_LEVEL_INFO, msg);
}

static void _logmsg(int level, const char *format, ...) {
  if (_ourLogger == NULL)
    return;

  va_list args;
  va_start(args, format);

  char buf[1024];
  vsnprintf(buf, 1024, format, args);
  (*_ourLogger)(level, buf);

  va_end(args);
}

static void _printWndInfo(const WndInfo *wi, char *out, int outLen) {
  if (out == NULL || outLen <= 0) return;
  if (wi == NULL) {
    out[0] = 0;
    return;
  }

  snprintf(out, (size_t)outLen, "xid=0x%X menuPath='%s' registrar=0x%p server=0x%p menuroot=0x%p", wi->xid, wi->menuPath,
           wi->registrar, wi->server, wi->menuroot);
}

static void _onNameAppeared(GDBusConnection *connection, const gchar *name, const gchar *name_owner, gpointer user_data) {
  if (_ourOnServiceAppearedCallback != NULL)
    (*((jrunnable) _ourOnServiceAppearedCallback))();
}

static void _onNameVanished(GDBusConnection *connection, const gchar *name, gpointer user_data) {
  if (_ourOnServiceVanishedCallback != NULL)
    (*((jrunnable) _ourOnServiceVanishedCallback))();
}

// NOTE: main-loop is necessary for communication with dbus (via glib and it's signals)
void runMainLoop(jlogger jlogger, jrunnable onAppmenuServiceAppeared, jrunnable onAppmenuServiceVanished) {
  glib_main_context = g_main_context_new();
  g_main_context_push_thread_default(glib_main_context);  // make this ctx default for current thread

  _ourLogger = jlogger;
  _ourOnServiceAppearedCallback = onAppmenuServiceAppeared;
  _ourOnServiceVanishedCallback = onAppmenuServiceVanished;
  g_bus_watch_name(G_BUS_TYPE_SESSION, DBUS_NAME, G_BUS_NAME_WATCHER_FLAGS_NONE, _onNameAppeared, _onNameVanished, NULL, NULL);
  // NOTE: Callbacks will be invoked in the thread-default main context of the thread you are calling g_bus_watch_name from.
  // _info("start watching for dbus name 'com.canonical.AppMenu.Registrar'");

  GMainLoop * main_loop = g_main_loop_new(glib_main_context, FALSE);
  g_main_loop_run(main_loop);
}

static void _onDbusOwnerChange(GObject *gobject, GParamSpec *pspec, gpointer user_data) {
  GDBusProxy *proxy = G_DBUS_PROXY(gobject);

  gchar *owner = g_dbus_proxy_get_name_owner(proxy);

  if (owner == NULL || owner[0] == '\0') {
    /* We only care about folks coming on the bus.  Exit quickly otherwise. */
    _info("new dbus owner is empty, nothing to do");
    g_free(owner);
    return;
  }

  if (g_strcmp0(owner, DBUS_NAME)) {
    /* We only care about this address, reject all others. */
    _info("new dbus owner is AppMenu.Registrar, nothing to do");
    g_free(owner);
    return;
  }

  if (user_data == NULL) {
    _error("_onDbusOwnerChange invoked with null user_data");
    g_free(owner);
    return;
  }

  // _logmsg(LOG_LEVEL_INFO, "new owner '%s'", owner);

  WndInfo *wi = (WndInfo *) user_data;

  if (wi->menuPath == NULL) {
    _error("_onDbusOwnerChange invoked with empty WndInfo");
    g_free(owner);
    return;
  }

  char buf[1024];
  _printWndInfo(wi, buf, 1024);
  _logmsg(LOG_LEVEL_INFO, "window: '%s'", buf);

  GError *error = NULL;
  g_dbus_proxy_call_sync(wi->registrar, "RegisterWindow",
                         g_variant_new("(uo)",
                         wi->xid,
                         wi->menuPath),
                         G_DBUS_CALL_FLAGS_NONE, -1, NULL, &error);
  if (error != NULL) {
    _logmsg(LOG_LEVEL_ERROR, "Unable to re-register window, error: %s", error->message);
    g_error_free(error);
    g_free(owner);
    return;
  }

  // _info("Window has been successfully re-registered");
  g_free(owner);
}

static void _releaseMenuItem(gpointer data) {
  if (data != NULL) {
    g_list_free_full(dbusmenu_menuitem_take_children((DbusmenuMenuitem *) data), _releaseMenuItem);
    g_object_unref(G_OBJECT(data));
  }
}

static void _unregisterWindow(guint32 xid, GDBusProxy * registrar) {
  // NOTE: sync call g_dbus_proxy_call_sync(wi->registrar, "UnregisterWindow", g_variant_new("(u)", wi->xid), G_DBUS_CALL_FLAGS_NONE, -1, NULL, &error)
  // under ubuntu18 (with GlobalMenu plugin) executes several minutes.
  // We make async call and don't care about results and errors (i.e. NULL callbacks)
  g_dbus_proxy_call(registrar,
                    "UnregisterWindow",
                    g_variant_new("(u)", xid),
                    G_DBUS_CALL_FLAGS_NONE, -1,
                    NULL,
                    NULL,
                    NULL);
}

static void _releaseWindow(WndInfo *wi) {
  if (wi == NULL) return;
  if (wi->menuPath == NULL) {
    _error("try to release empty WndInfo");
    return;
  }

  free(wi->menuPath);
  wi->menuPath = NULL;

  if (wi->menuroot != NULL) {
    _releaseMenuItem(wi->menuroot);
    wi->menuroot = NULL;
  }

  if (wi->server != NULL) {
    g_object_unref(wi->server);
    wi->server = NULL;
  }

  if (wi->registrar != NULL) {
    _unregisterWindow(wi->xid, wi->registrar);
    if (wi->linkedXids != NULL) {
      for (GList* l = wi->linkedXids; l != NULL; l = l->next) {
          const guint32 xid = ((unsigned long)l->data) & 0xFFFFFFFF;
          _unregisterWindow(xid, wi->registrar);
      }
    }

    g_object_unref(wi->registrar);
    wi->registrar = NULL;
  }

  if (wi->linkedXids != NULL) {
    g_list_free(wi->linkedXids);
    wi->linkedXids = NULL;
  }

  if (wi->onReleaseCallback != NULL) {
    (*wi->onReleaseCallback)();
    wi->onReleaseCallback = NULL;
  }
  free(wi);
}

void createMenuRootForWnd(WndInfo *wi) {
    if (wi->menuroot != NULL) {
        _releaseMenuItem(wi->menuroot);
        wi->menuroot = NULL;
    }

    wi->menuroot = dbusmenu_menuitem_new();
    if (wi->menuroot == NULL) {
        _error("can't create menuitem for new root");
        return;
    }

    g_object_set_data(G_OBJECT(wi->menuroot), MENUITEM_JHANDLER_PROPERTY, wi->jhandler);
    dbusmenu_menuitem_property_set(wi->menuroot, DBUSMENU_MENUITEM_PROP_LABEL, "DBusMenuRoot");

    if (wi->server == NULL)
        _error("can't set new root because wi->server is null");
    else
        dbusmenu_server_set_root(wi->server, wi->menuroot);
}

WndInfo *registerWindow(guint32 windowXid, jeventcallback handler) {
  // _info("register new window");

  WndInfo *wi = (WndInfo *) malloc(sizeof(WndInfo));
  memset(wi, 0, sizeof(WndInfo));

  wi->xid = (guint32) windowXid;
  wi->menuPath = malloc(64);
  sprintf(wi->menuPath, "/com/canonical/menu/0x%x", windowXid);

  wi->menuroot = dbusmenu_menuitem_new();
  if (wi->menuroot == NULL) {
    _error("can't create menuitem for new root");
    _releaseWindow(wi);
    return NULL;
  }

  g_object_set_data(G_OBJECT(wi->menuroot), MENUITEM_JHANDLER_PROPERTY, handler);
  dbusmenu_menuitem_property_set(wi->menuroot, DBUSMENU_MENUITEM_PROP_LABEL, "DBusMenuRoot");

  wi->server = dbusmenu_server_new(wi->menuPath);
  dbusmenu_server_set_root(wi->server, wi->menuroot);

  wi->registrar = g_dbus_proxy_new_for_bus_sync(G_BUS_TYPE_SESSION,
                                                G_DBUS_PROXY_FLAGS_NONE,
                                                NULL,
                                                DBUS_NAME,
                                                REG_OBJECT,
                                                REG_IFACE,
                                                NULL, NULL);
  if (wi->registrar == NULL) {
    // probably need to watch for registrar on dbus
    // guint watcher = g_bus_watch_name(G_BUS_TYPE_SESSION, DBUS_NAME, G_BUS_NAME_WATCHER_FLAGS_NONE, on_registrar_available, on_registrar_unavailable);
    _error("can't obtain registrar");
    _releaseWindow(wi);
    return NULL;
  }

  char buf[1024];
  _printWndInfo(wi, buf, 1024);
  _logmsg(LOG_LEVEL_INFO, "new window info: %s", buf);

  GError *error = NULL;
  g_dbus_proxy_call_sync(
    wi->registrar,
    "RegisterWindow",
    g_variant_new("(uo)", windowXid, wi->menuPath),
    G_DBUS_CALL_FLAGS_NONE,
    -1,
    NULL,
    &error);

  if (error != NULL) {
    _logmsg(LOG_LEVEL_ERROR, "Unable to register window, error: %s", error->message);
    g_error_free(error);
    _releaseWindow(wi);
    return NULL;
  }

  wi->jhandler = handler;
  g_signal_connect(wi->registrar, "notify::g-name-owner", G_CALLBACK(_onDbusOwnerChange), wi);

  return wi;
}

void bindNewWindow(WndInfo * wi, guint32 windowXid) {
  if (wi == NULL || wi->server == NULL || wi->menuPath == NULL)
    return;

  GError *error = NULL;
  g_dbus_proxy_call_sync(
    wi->registrar,
    "RegisterWindow",
    g_variant_new("(uo)", windowXid, wi->menuPath),
    G_DBUS_CALL_FLAGS_NONE,
    -1,
    NULL,
    &error);

  if (error != NULL) {
    _logmsg(LOG_LEVEL_ERROR, "Unable to bind new window, menu-server '%s', error: %s", wi->menuPath, error->message);
    g_error_free(error);
    return;
  }

  // _logmsg(LOG_LEVEL_INFO, "bind new window 0x%lx", windowXid);
  wi->linkedXids = g_list_append(wi->linkedXids, (void *)(unsigned long)windowXid);
}

void unbindWindow(WndInfo * wi, guint32 windowXid) {
  if (wi == NULL || wi->server == NULL || wi->menuPath == NULL)
    return;

  // _logmsg(LOG_LEVEL_INFO, "unbind window 0x%lx", windowXid);
  _unregisterWindow(windowXid, wi->registrar);

  if (wi->linkedXids != NULL)
    wi->linkedXids = g_list_remove(wi->linkedXids, (void *)(unsigned long)windowXid);
}

static gboolean _execReleaseWindow(gpointer user_data) {
  _releaseWindow(user_data);
  return FALSE;
}

void releaseWindowOnMainLoop(WndInfo *wi, jrunnable onReleased) {
  // _info("scheduled releaseWindowOnMainLoop");
  if (wi != NULL)
    wi->onReleaseCallback = onReleased;
  g_idle_add(_execReleaseWindow, wi);
}

static const char * _getItemLabel(DbusmenuMenuitem *item) {
  const gchar * label = dbusmenu_menuitem_property_get(item, DBUSMENU_MENUITEM_PROP_LABEL);
  return label == NULL ? "null" : label;
}

void clearRootMenu(WndInfo *wi) {
  if (wi == NULL || wi->menuroot == NULL) return;
  // _info("clear root");
  g_list_free_full(dbusmenu_menuitem_take_children(wi->menuroot), _releaseMenuItem);
}

void clearMenu(DbusmenuMenuitem *menu) {
  if (menu == NULL) return;
  // _logmsg(LOG_LEVEL_INFO, "clear menu %s", _getItemLabel(menu));
  g_list_free_full(dbusmenu_menuitem_take_children(menu), _releaseMenuItem);
}

//
// menu <==> internal_node
// menuItem <==> leaf
//

static const char *_type2str(int type) {
  switch (type) {
    case EVENT_OPENED:
      return "event-opened";
    case EVENT_CLOSED:
      return "event-closed";
    case EVENT_CLICKED:
      return "event-clicked";
    case SIGNAL_ACTIVATED:
      return "sig-activated";
    case SIGNAL_ABOUT_TO_SHOW:
      return "sig-about-to-show";
    case SIGNAL_SHOWN:
      return "sig-shown";
    case SIGNAL_CHILD_ADDED:
      return "sig-child-added";
    default:
        return "unknown event type";
  }
}

static void _handleItemSignal(DbusmenuMenuitem *item, int type) {
//    const gchar * label = dbusmenu_menuitem_property_get(item, DBUSMENU_MENUITEM_PROP_LABEL);
//    _logmsg(LOG_LEVEL_INFO, "_onItemSignal %s, item '%s'", _type2str(type), label == NULL ? "null" : label);

  gpointer jhandler = g_object_get_data(G_OBJECT(item), MENUITEM_JHANDLER_PROPERTY);
  if (jhandler == NULL) {
    _error("_onItemSignal: null jhandler");
    return;
  }

  const int uid = dbusmenu_menuitem_property_get_int(item, MENUITEM_UID_PROPERTY);
  (*((jeventcallback) jhandler))(uid, type);
}


static void _onItemEvent(DbusmenuMenuitem *item, const char *event, GVariant * info, guint32 time) {
//    _logmsg(LOG_LEVEL_INFO, "_onItemEvent %s", event);

  int eventType = -1;
  if (strcmp(DBUSMENU_MENUITEM_EVENT_OPENED, event) == 0)
    eventType = EVENT_OPENED;
  else if (strcmp(DBUSMENU_MENUITEM_EVENT_CLOSED, event) == 0)
    eventType = EVENT_CLOSED;
  else if (strcmp(DBUSMENU_MENUITEM_EVENT_ACTIVATED, event) == 0)
    eventType = EVENT_CLICKED;
  else
    _error("unknown event type");

  //_logmsg(LOG_LEVEL_INFO, "time %d", time);
  _handleItemSignal(item, eventType);
}

static void _onItemActivated(DbusmenuMenuitem *item) {
  _handleItemSignal(item, SIGNAL_ACTIVATED);
}

static void _onItemAboutToShow(DbusmenuMenuitem *item) {
  _handleItemSignal(item, SIGNAL_ABOUT_TO_SHOW);
}

static void _onItemShowToUser(DbusmenuMenuitem *item, guint32 time) {
  _handleItemSignal(item, SIGNAL_SHOWN);
}

static void _onItemChildAdded(DbusmenuMenuitem *parent, DbusmenuMenuitem *child, guint32 pos) {
  _handleItemSignal(parent, SIGNAL_CHILD_ADDED);
}

DbusmenuMenuitem *addRootMenu(WndInfo *wi, int uid, const char * label) {
  if (wi == NULL || wi->menuroot == NULL)
    return NULL;
  // _logmsg(LOG_LEVEL_INFO, "add root %d", uid);
  return addMenuItem(wi->menuroot, uid, label, true, -1);
}

DbusmenuMenuitem *addMenuItem(DbusmenuMenuitem *parent, int uid, const char * label, int type, int position) {
  // _logmsg(LOG_LEVEL_INFO, "add menu item %s (%d) [p %s]", label, uid, _getItemLabel(parent));

  DbusmenuMenuitem *item = dbusmenu_menuitem_new();

  dbusmenu_menuitem_property_set_int(item, MENUITEM_UID_PROPERTY, uid);
  dbusmenu_menuitem_property_set(item, DBUSMENU_MENUITEM_PROP_LABEL, label);
  if (type == ITEM_SUBMENU)
    dbusmenu_menuitem_property_set(item, DBUSMENU_MENUITEM_PROP_CHILD_DISPLAY, DBUSMENU_MENUITEM_CHILD_DISPLAY_SUBMENU);
  else if (type == ITEM_CHECK)
    dbusmenu_menuitem_property_set(item, DBUSMENU_MENUITEM_PROP_TOGGLE_TYPE, DBUSMENU_MENUITEM_TOGGLE_CHECK);
  else if (type == ITEM_RADIO)
    dbusmenu_menuitem_property_set(item, DBUSMENU_MENUITEM_PROP_TOGGLE_TYPE, DBUSMENU_MENUITEM_TOGGLE_RADIO);

  dbusmenu_menuitem_property_set_bool(item, DBUSMENU_MENUITEM_PROP_VISIBLE, TRUE);

  g_signal_connect(G_OBJECT(item), DBUSMENU_MENUITEM_SIGNAL_EVENT, G_CALLBACK(_onItemEvent), NULL);
  g_signal_connect(G_OBJECT(item), DBUSMENU_MENUITEM_SIGNAL_ABOUT_TO_SHOW, G_CALLBACK(_onItemAboutToShow), NULL);
  // g_signal_connect(G_OBJECT(item), DBUSMENU_MENUITEM_SIGNAL_SHOW_TO_USER, G_CALLBACK(_onItemShowToUser), NULL);
  g_signal_connect(G_OBJECT(item), DBUSMENU_MENUITEM_SIGNAL_ITEM_ACTIVATED, G_CALLBACK(_onItemActivated), NULL);
  // g_signal_connect(G_OBJECT(item), DBUSMENU_MENUITEM_SIGNAL_CHILD_ADDED, G_CALLBACK(_onItemChildAdded), NULL);

  if (parent != NULL) {
    gpointer data = g_object_get_data(G_OBJECT(parent), MENUITEM_JHANDLER_PROPERTY);
    if (data == NULL)
      _logmsg(LOG_LEVEL_ERROR, "parent of item %d hasn't jhandler", uid);
    g_object_set_data(G_OBJECT(item), MENUITEM_JHANDLER_PROPERTY, data);
    if (position < 0)
      dbusmenu_menuitem_child_append(parent, item);
    else
      dbusmenu_menuitem_child_add_position(parent, item, (guint)position);
  }

  return item;
}

DbusmenuMenuitem* addSeparator(DbusmenuMenuitem * parent, int uid, int position) {
  DbusmenuMenuitem* item = dbusmenu_menuitem_new();
  dbusmenu_menuitem_property_set(item, DBUSMENU_MENUITEM_PROP_TYPE, "separator");
  dbusmenu_menuitem_property_set_int(item, MENUITEM_UID_PROPERTY, uid);
  dbusmenu_menuitem_property_set_bool(item, DBUSMENU_MENUITEM_PROP_VISIBLE, TRUE);
  if (parent != NULL) {
    if (position < 0)
      dbusmenu_menuitem_child_append(parent, item);
    else
      dbusmenu_menuitem_child_add_position(parent, item, (guint)position);
  }

  return item;
}

void reorderMenuItem(DbusmenuMenuitem * parent, DbusmenuMenuitem* item, int position) { dbusmenu_menuitem_child_reorder(parent, item, (guint)position); }

void removeMenuItem(DbusmenuMenuitem * parent, DbusmenuMenuitem* item) { dbusmenu_menuitem_child_delete(parent, item); }

static gboolean _showMenuItem(gpointer item) {
    dbusmenu_menuitem_show_to_user(item, 0);
    return FALSE;
}

static guint
execInMainContext(GSourceFunc func, gpointer data) {
    GSource *source = g_timeout_source_new(0);
    g_source_set_callback(source, func, data, NULL);
    guint result = g_source_attach(source, glib_main_context);
    g_source_unref(source);
    return result;
}

void showMenuItem(DbusmenuMenuitem* item) {
    execInMainContext(_showMenuItem, item);
}

void setItemLabel(DbusmenuMenuitem *item, const char *label) {
  dbusmenu_menuitem_property_set(item, DBUSMENU_MENUITEM_PROP_LABEL, label);
}

void setItemEnabled(DbusmenuMenuitem *item, bool isEnabled) {
  dbusmenu_menuitem_property_set_bool(item, DBUSMENU_MENUITEM_PROP_ENABLED, (gboolean) isEnabled);
}

void setItemIcon(DbusmenuMenuitem *item, const char *iconBytesPng, int iconBytesCount) {
  dbusmenu_menuitem_property_set_byte_array(item, DBUSMENU_MENUITEM_PROP_ICON_DATA, (guchar*)iconBytesPng, (gsize)iconBytesCount);
  // NOTE: memory copied (try to call memset(iconBytesPng, 0, iconBytesCount) after)
}

// java modifiers
static const int SHIFT_MASK          = 1 << 0;
static const int CTRL_MASK           = 1 << 1;
static const int META_MASK           = 1 << 2;
static const int ALT_MASK            = 1 << 3;

void setItemShortcut(DbusmenuMenuitem *item, int jmodifiers, int x11keycode) {
  char* xname = XKeysymToString((KeySym)x11keycode);
  if (xname == NULL) {
    // _logmsg(LOG_LEVEL_ERROR, "XKeysymToString returns null for x11keycode=%d", x11keycode);
    return;
  }
  // _logmsg(LOG_LEVEL_INFO, "XKeysymToString returns %s for x11keycode=%d", xname, x11keycode);

  GVariantBuilder builder;
  g_variant_builder_init(&builder, G_VARIANT_TYPE_ARRAY);
  if ((jmodifiers & SHIFT_MASK) != 0)
    g_variant_builder_add(&builder, "s",  DBUSMENU_MENUITEM_SHORTCUT_SHIFT);
  if ((jmodifiers & CTRL_MASK) != 0)
    g_variant_builder_add(&builder, "s", DBUSMENU_MENUITEM_SHORTCUT_CONTROL);
  if ((jmodifiers & ALT_MASK) != 0)
    g_variant_builder_add(&builder, "s", DBUSMENU_MENUITEM_SHORTCUT_ALT);
  if ((jmodifiers & META_MASK) != 0)
    g_variant_builder_add(&builder, "s", DBUSMENU_MENUITEM_SHORTCUT_SUPER);

  g_variant_builder_add(&builder, "s", xname);

  GVariant *insideArr = g_variant_builder_end(&builder);
  g_variant_builder_init(&builder, G_VARIANT_TYPE_ARRAY);
  g_variant_builder_add_value(&builder, insideArr);

  GVariant *outsideArr = g_variant_builder_end(&builder);
  dbusmenu_menuitem_property_set_variant(item, DBUSMENU_MENUITEM_PROP_SHORTCUT, outsideArr);
}

void toggleItemStateChecked(DbusmenuMenuitem *item, bool isChecked) {
  const int nOn = DBUSMENU_MENUITEM_TOGGLE_STATE_CHECKED;
  const int nOff = DBUSMENU_MENUITEM_TOGGLE_STATE_UNCHECKED;
  const int ncheck = isChecked ? nOn : nOff;
  if (dbusmenu_menuitem_property_get_int(item, DBUSMENU_MENUITEM_PROP_TOGGLE_STATE) != ncheck) {
    // _logmsg(LOG_LEVEL_INFO, "item %s changes checked-state: %d -> %d", _getItemLabel(item), dbusmenu_menuitem_property_get_int(item, DBUSMENU_MENUITEM_PROP_TOGGLE_STATE), ncheck);
    dbusmenu_menuitem_property_set_int(item, DBUSMENU_MENUITEM_PROP_TOGGLE_STATE, ncheck);
  }
}

static gboolean _execJRunnable(gpointer user_data) {
  (*((jrunnable) user_data))();
  return FALSE;
}

void execOnMainLoop(jrunnable run) {
  // _info("scheduled execOnMain");
  if (glib_main_context == NULL) {
      _logmsg(LOG_LEVEL_ERROR, "execOnMainLoop: glib_main_context wasn't initialized");
      return;
  }

  execInMainContext(_execJRunnable, run);
}